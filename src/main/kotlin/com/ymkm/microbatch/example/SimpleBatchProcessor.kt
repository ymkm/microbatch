package com.ymkm.microbatch.example

import com.ymkm.microbatch.BatchProcessor
import com.ymkm.microbatch.Job
import com.ymkm.microbatch.JobResult

class SimpleBatchProcessor : BatchProcessor {
    override suspend fun processBatch(jobs: List<Job>): List<JobResult> {
        return jobs.map {
            try {
                it.execute()
            } catch (e: Exception) {
                 JobResult(false, e.message ?: "Failed to run job")
            }
        }
    }
}