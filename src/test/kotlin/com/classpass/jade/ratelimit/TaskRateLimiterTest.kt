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
        val throttle = TaskRateLimiter(1000, Duration.ofSeconds(1))

        val numTasks = 1000
        // Array of `false`, tasks mark their work complete
        val completedWork = AtomicReferenceArray<Boolean?>(numTasks)

        val futures = (0 until numTasks).map { taskNum ->
            throttle.submit {
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
        val throttle = TaskRateLimiter(5, Duration.ofSeconds(1))

        // Process 11 tasks so it takes ≈ 2 seconds
        val numTasks = 11
        // Log completion times relative to start to check intervals
        val completedTasks = AtomicInteger(0)

        val duration = measureTimeMillis {
            val futures = (0 until numTasks).map { taskNum ->
                throttle.submit {
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