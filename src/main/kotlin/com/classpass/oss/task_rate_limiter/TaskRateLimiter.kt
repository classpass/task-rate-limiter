/**
 * Copyright 2021 ClassPass
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package com.classpass.oss.task_rate_limiter

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import javax.annotation.concurrent.GuardedBy

/**
 * Limits the number of tasks executed during a given time slice.
 */
public class TaskRateLimiter(
    numPerDuration: Int,
    duration: Duration
) : AutoCloseable {
    private val executorService = Executors.newSingleThreadScheduledExecutor()
    private val delay = duration.toNanos() / numPerDuration

    // Invariants:
    // timerFuture == null -> queue is empty
    // timerFuture != null -> implies nothing—queue may or may not be empty
    private val lock = Object()

    @GuardedBy("lock")
    private val queue = ArrayDeque<() -> Unit>()

    /**
     * This field is null until work has been submitted, whereupon it is a handle on a task that
     * the {@link executorService} will execute after {@link delay}. When that timer task is run,
     * it clears this future and, if there is work in the queue, schedules a new timer and
     * executes the next bit of work.
     */
    @GuardedBy("lock")
    private var timerFuture: ScheduledFuture<Unit>? = null

    /**
     * Submit at a task to be executed abiding by the rate limit. The task will be executed
     * immediately if no other work is currently enqueued.
     *
     * The timer is a task scheduled with the executor that wakes up each {@link delay}, executes
     * the first item in the queue, and starts a new timer to check for work in {@link delay} time.
     */
    public fun <T> submit(task: () -> CompletableFuture<T>): CompletableFuture<T> {
        val future = synchronized(lock) {
            if (timerFuture == null) {
                logger.debug("Starting task immediately")
                // Start timer to handle work after the imminently-executed task is complete
                startTimer()
                null
            } else {
                // Only need to enqueue work here—there is already a timer scheduled and it will
                // pick up the first task in the queue
                logger.debug("Enqueueing task")
                enqueueWork(task)
            }
        }
        return future ?: runTask(task, CompletableFuture<T>())
    }

    /**
     * Run a task immediately. The given CompletableFuture will be marked with an exceptional result if the task
     * completes exceptionally or invoking the task throws an exception.
     *
     * @return The passed-in CompletableFuture
     */
    private fun <T> runTask(
        task: () -> CompletableFuture<T>,
        completableFuture: CompletableFuture<T>
    ): CompletableFuture<T> {
        try {
            task().whenComplete { value, throwable ->
                if (throwable != null) {
                    logger.debug("Task completed exceptionally", throwable)
                    completableFuture.completeExceptionally(throwable)
                } else {
                    logger.debug("Task completed successfully")
                    completableFuture.complete(value)
                }
            }
        } catch (t: Throwable) {
            logger.warn("Exception while executing task")
            completableFuture.completeExceptionally(t)
        }
        return completableFuture
    }

    /**
     * Enqueue work to be run later in a wrapper task. When the submitted work is executed,
     * the wrapper task will mark it as complete.
     */
    @GuardedBy("lock") // Only called by submit()
    private fun <T> enqueueWork(task: () -> CompletableFuture<T>): CompletableFuture<T> {
        val completableFuture = CompletableFuture<T>()
        queue.add {
            runTask(task, completableFuture)
        }
        return completableFuture
    }

    /**
     * Give the {@link executorService} a task to run after {@link delay} that clears the
     * {@link timerFuture} and, if the {@link queue} is not empty, executes that task &
     * schedules a new task with the {@link executorService}.
     */
    @GuardedBy("lock") // Only called by submit() or the synchronized block herein
    private fun startTimer() {
        timerFuture = executorService.schedule(
            Callable {
                val taskWrapper = synchronized(lock) {
                    val taskWrapper = queue.removeFirstOrNull()
                    if (taskWrapper != null) {
                        logger.debug("More work to do, starting timer")
                        startTimer()
                    } else {
                        logger.debug("Clearing timer")
                        timerFuture = null
                    }
                    taskWrapper
                }
                // Run the retrieved task, if there was one
                taskWrapper?.invoke()
            },
            delay, TimeUnit.NANOSECONDS
        )
    }

    override fun close() {
        executorService.shutdown()
    }

    public companion object {
        private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    }
}
