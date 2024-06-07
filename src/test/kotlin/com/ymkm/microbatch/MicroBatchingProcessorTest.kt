package com.ymkm.microbatch

import com.ymkm.microbatch.TestFixtures.*
import com.ymkm.microbatch.example.SimpleBatchProcessor
import kotlinx.coroutines.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTime

@ExperimentalCoroutinesApi
class MicroBatchingProcessorTest {

    @Nested
    inner class Setup {
        @Test
        fun `returns an error with zero batch size`() {
            assertThrows<IllegalArgumentException> {
                MicroBatchingProcessor(SimpleBatchProcessor(), 0)
            }
        }

        @Test
        fun `returns an error with negative batch size`() {
            assertThrows<IllegalArgumentException> {
                MicroBatchingProcessor(SimpleBatchProcessor(), -10)
            }
        }

        @Test
        fun `returns an error with large batch size`() {
            assertThrows<IllegalArgumentException> {
                MicroBatchingProcessor(SimpleBatchProcessor(), 1000000000)
            }
        }

        @Test
        fun `returns an error with zero frequency `() {
            assertThrows<IllegalArgumentException> {
                MicroBatchingProcessor(SimpleBatchProcessor(), batchFrequencyMillis = 0)
            }
        }

        @Test
        fun `returns an error with negative frequency`() {
            assertThrows<IllegalArgumentException> {
                MicroBatchingProcessor(SimpleBatchProcessor(), batchFrequencyMillis = -10)
            }
        }

        @Test
        fun `returns an error with large frequency`() {
            assertThrows<IllegalArgumentException> {
                MicroBatchingProcessor(SimpleBatchProcessor(), batchFrequencyMillis = 1000000000)
            }
        }
    }

    @Nested
    inner class SubmitJob {
        @Test
        fun `returns a Deferred (promise) when a job is submitted`() {
            runBlocking {
                val processor = MicroBatchingProcessor(SimpleBatchProcessor())
                val result = processor.submitJob(AdditionJob(2, 3))
                assertThat(result.await().message).isEqualTo("5")
            }
        }

        @Test
        fun `processes jobs after batchFrequency has elapsed`() {
            runBlocking {
                val testFrequency = 1000L
                val duration = measureTime {
                    val processor =
                        MicroBatchingProcessor(SimpleBatchProcessor(), batchFrequencyMillis = testFrequency)
                    val result = processor.submitJob(AdditionJob(2, 3))
                    result.await().message
                }
                assertThat(duration).isGreaterThan(testFrequency.milliseconds)
            }
        }

        @Test
        fun `processes jobs after batchFrequency even when batch is not full`() {
            runBlocking {
                val processor =
                    MicroBatchingProcessor(SimpleBatchProcessor())
                val deferred1 = processor.submitJob(AdditionJob(2, 3))
                delay(200)
                val deferred2 = processor.submitJob(AdditionJob(5, 1))
                assertThat(deferred1.await().message).isEqualTo("5")
                assertThat(deferred2.await().message).isEqualTo("6")
            }
        }

        @Test
        fun `processes jobs no faster than permitted by batch size and batchFrequencyMillis`() {
            val testFrequency = 200L

            runBlocking {
                val processor =
                    MicroBatchingProcessor(SimpleBatchProcessor(), batchFrequencyMillis = testFrequency)
                val startTime = System.currentTimeMillis()
                val firstBatch = (1..3).map { processor.submitJob(AdditionJob(1, it)) }
                val secondBatch = (5..6).map { processor.submitJob(AdditionJob(1, it)) }

                val threshold = testFrequency * 2 // first batch should complete before this, and second batch after

                launch {
                    secondBatch.awaitAll()
                    assertThat(System.currentTimeMillis() - startTime).isGreaterThan(threshold)
                }

                launch {
                    firstBatch.awaitAll()
                    assertThat(System.currentTimeMillis() - startTime).isLessThan(threshold)
                }
            }
        }

        @Test
        fun `runs jobs in parallel when provided with a parallel batch processor`() {
            runBlocking {
                val processor = MicroBatchingProcessor(ParallelBatchProcessor())
                val totalJobs = 20
                val delay = 100L
                val jobList = (1..totalJobs).map {
                    SlowJob(1, it, delay)
                }
                val duration = measureTime {
                    jobList.map { processor.submitJob(it) }.awaitAll()
                }
                assertThat(duration).isLessThan((totalJobs * delay).milliseconds)
            }
        }

        @Test
        fun `continues with other jobs if one throws an Exception`() {
            runBlocking {
                val processor = MicroBatchingProcessor(SimpleBatchProcessor())
                val jobList = mutableListOf<Job>()
                jobList += (1..3).map {
                    AdditionJob(1, it)
                }
                jobList.add(ExceptionThrowingJob())
                jobList += (5..6).map {
                    AdditionJob(1, it)
                }
                val results = jobList.map { processor.submitJob(it) }.awaitAll()
                val failedJobCount = results.count { !it.success }
                val successfulJobCount = results.count { it.success }
                assertThat(failedJobCount).isEqualTo(1)
                assertThat(successfulJobCount).isEqualTo(5)
            }
        }

    }

    @Nested
    inner class Shutdown {
        @Test
        fun `ensures that remaining jobs are run first`() {
            runBlocking {
                val processor = MicroBatchingProcessor(SimpleBatchProcessor())
                val result = processor.submitJob(AdditionJob(2, 3))
                processor.shutdown()
                assertThat(result.await().message).isEqualTo("5")
            }
        }

        @Test
        fun `can be called before submitting jobs without error`() {
            runBlocking {
                val processor = MicroBatchingProcessor(SimpleBatchProcessor())
                processor.shutdown()
            }
        }
    }
}

