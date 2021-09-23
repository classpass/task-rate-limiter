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
package com.classpass.jade.ratelimit

import org.junit.jupiter.api.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReferenceArray
import kotlin.system.measureTimeMillis
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

internal class TaskRateLimiterTest {
    @Test
    fun allTasksComplete() {
        val taskRateLimiter = TaskRateLimiter(1000, Duration.ofSeconds(1))

        val numTasks = 1000
        // Array of null tasks mark their work complete
        val completedWork = AtomicReferenceArray<Boolean?>(numTasks)

        val futures = (0 until numTasks).map { taskNum ->
            taskRateLimiter.submit {
                CompletableFuture.supplyAsync {
                    logger.debug("Working on $taskNum")
                    completedWork.set(taskNum, true)
                }
            }
        }

        futures.forEach { it.join() }

        (0 until completedWork.length()).forEach {
            val completed = completedWork.get(it)
            assertNotNull(completed)
            assertTrue(completed)
        }
    }

    @Test
    fun rateLimitRespected() {
        val taskRateLimiter = TaskRateLimiter(5, Duration.ofSeconds(1))

        // Process 11 tasks so it takes slightly more than 2 seconds
        val numTasks = 11
        val completedTasks = AtomicInteger(0)

        val duration = measureTimeMillis {
            val futures = (0 until numTasks).map { taskNum ->
                taskRateLimiter.submit {
                    CompletableFuture.supplyAsync {
                        logger.debug("Working on $taskNum")
                        completedTasks.incrementAndGet()
                    }
                }
            }

            futures.forEach { it.join() }
        }

        assertEquals(numTasks, completedTasks.get())
        assertTrue(duration > 2000, "Tasks ran too quickly (expected >2000ms, actual ${duration}ms")
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    }
}
