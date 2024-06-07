package com.ymkm.microbatch

import com.ymkm.microbatch.example.RandomNumberJob
import com.ymkm.microbatch.example.SimpleBatchProcessor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@ExperimentalCoroutinesApi
class IntegrationTest {

    @Test
    fun `returns the result of a Job that makes a network request`() {
        runBlocking {
            val processor = MicroBatchingProcessor(SimpleBatchProcessor())
            val result = processor.submitJob(RandomNumberJob())
            assertThat(result.await().message.toInt()).isGreaterThan(0).isLessThan(101)
        }
    }
}