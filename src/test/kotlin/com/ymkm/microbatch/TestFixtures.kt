package com.ymkm.microbatch

import kotlinx.coroutines.*

class TestFixtures {

    class AdditionJob(val num1: Int, val num2: Int) : Job {
        override suspend fun execute(): JobResult {
            return JobResult(true, "${num1 + num2}")
        }
    }

    class SlowJob(val num1: Int, val num2: Int, private val delay: Long = 1000L) : Job {
        override suspend fun execute(): JobResult {
            delay(delay)
            return JobResult(true, "${num1 + num2}")
        }
    }

    class ExceptionThrowingJob : Job {
        override suspend fun execute() = throw Exception("Failed to run")
    }

    class ParallelBatchProcessor : BatchProcessor {
        override suspend fun processBatch(jobs: List<Job>): List<JobResult> = withContext(Dispatchers.IO) {
            jobs.map {
                async {
                    it.execute()
                }
            }.awaitAll()
        }
    }
}