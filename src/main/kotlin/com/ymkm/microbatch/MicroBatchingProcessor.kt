package com.ymkm.microbatch

import kotlinx.coroutines.*
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Data class representing the result of a job execution.
 *
 * @property success Indicates whether the job was successful.
 * @property message A message providing additional information about the job result.
 */
data class JobResult(val success: Boolean, val message: String)

/**
 * a job that can be executed. Clients of the Microbatch extend this interface
 */
interface Job {
    /**
     * Executes the job.
     *
     * @return The result of the job execution.
     */
    suspend fun execute(): JobResult
}

/**
 * a processor that executes a list of jobs, in sequence or in parallel as required
 * @return
 */
interface BatchProcessor {
    /**
     * Processes a batch of jobs.
     *
     * @param jobs The list of jobs to be processed.
     * @return The list of results for each job.
     */
    suspend fun processBatch(jobs: List<Job>): List<JobResult>
}

/**
 * Class responsible for micro-batching job processing.
 *
 * @property batchProcessor The batch processor used to process batches of jobs
 * @property batchSize The maximum number of jobs to include in a single batch. Should be between {@value #MAX_BATCH_SIZE}.
 * @property batchFrequencyMillis The frequency in milliseconds at which batches are processed
 */
class MicroBatchingProcessor(
    private val batchProcessor: BatchProcessor,
    private val batchSize: Int = 3,
    private val batchFrequencyMillis: Long = 100,
) {
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
    private val jobQueue = LinkedBlockingQueue<Pair<Job, CompletableDeferred<JobResult>>>()
    private var isRunning = AtomicBoolean(true)
    private var count = AtomicInteger()

    init {
        require(batchSize > MIN_BATCH_SIZE) { "batchSize must be at least $MIN_BATCH_SIZE" }
        require(batchSize < MAX_BATCH_SIZE) { "batchSize must be at most $MAX_BATCH_SIZE" }
        require(batchFrequencyMillis > MIN_FREQUENCY_MILLIS) { "batchFrequencyMillis must be at least $MIN_FREQUENCY_MILLIS" }
        require(batchFrequencyMillis < MAX_FREQUENCY_MILLIS) { "batchFrequencyMillis must be at most $MAX_FREQUENCY_MILLIS" }
        startBatching()
    }

    /**
     * Submits a job to be processed.
     *
     * @param job The job to be submitted.
     * @return A deferred result of the job execution.
     */
    fun submitJob(job: Job): Deferred<JobResult> {
        val deferred = CompletableDeferred<JobResult>()
        scope.launch {
            jobQueue.put(job to deferred)
        }
        return deferred
    }

    private fun startBatching() {
        scope.launch {
            while (isRunning.get()) {
                delay(batchFrequencyMillis)
                processBatch()
            }
        }
    }

    private suspend fun processBatch() {
        val jobs = mutableListOf<Pair<Job, CompletableDeferred<JobResult>>>()
        jobQueue.drainTo(jobs, batchSize)
        if (jobs.isNotEmpty()) {
            val results = batchProcessor.processBatch(jobs.map { it.first })
            count.incrementAndGet()
            results.forEachIndexed { index, result ->
                jobs[index].second.complete(result)
            }
        }
    }

    /**
     * Shuts down the processor, ensuring all remaining jobs are processed.
     */
    suspend fun shutdown() {
        isRunning.set(false)
        scope.coroutineContext[Job]?.cancelAndJoin() // Cancel and wait for the scope to complete
        processBatch() // Process remaining jobs
    }

    companion object {
        const val MAX_BATCH_SIZE = 1000
        const val MIN_BATCH_SIZE = 2
        const val MIN_FREQUENCY_MILLIS = 10
        const val MAX_FREQUENCY_MILLIS = 10 * 60 * 1000 // 10 minutes
    }
}