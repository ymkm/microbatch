package com.ymkm.microbatch

import com.ymkm.microbatch.example.RandomNumberJob
import com.ymkm.microbatch.example.SimpleBatchProcessor
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val processor = MicroBatchingProcessor(SimpleBatchProcessor())
    val results = (1..5).map { processor.submitJob(RandomNumberJob())}.awaitAll()
    println("Some random numbers between 0 and 100 are: ${results.joinToString { it.message }}")
}
