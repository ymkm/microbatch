# Micro-batching Library

This is a Kotlin library for micro-batching job processing.
It provides a simple and efficient way to process jobs in batches,
improving throughput and reducing the overhead of processing individual jobs.

## Usage

To use the micro-batching library:

1. Implement the `Job` interface for your specific job type.
1. Provide an implementation of `BatchProcessor`
1. Create a `MicroBatchingProcessor` instance, passing in your `BatchProcessor` and configuring
    batch size and frequency.
1. Submit jobs to the `MicroBatchingProcessor` using `submitJob()`. This returns a `Deferred<JobResult>` promise that can be
   awaited to obtain the job result.
1. When all jobs have been submitted, call `shutdown()` to ensure all remaining jobs are processed before the
   processor shuts down.

```kotlin
// Implement your Job
class MyJob(val data: String) : Job {
    override suspend fun execute(): JobResult {
        // Process the job, returning result as a String
        return JobResult(true, "Job processed: $data")
    }
}

// Implement a BatchProcessor
class MyBatchProcessor : BatchProcessor {
    override suspend fun processBatch(jobs: List<Job>): List<JobResult> {
        // Process the batch of jobs, handling Exceptions thrown by Jobs as appropriate
        return jobs.map { it.execute() }
    }
}

fun main() = runBlocking {
    // Create the MicroBatchingProcessor, passing in your BatchProcessor implementation
    val microBatchingProcessor = MicroBatchingProcessor(MyBatchProcessor(), batchSize = 5, batchFrequencyMillis = 350)

    // Submit jobs
    val jobs = listOf(MyJob("one"), MyJob("two"), MyJob("three"))
    val jobResults = jobs.map { microBatchingProcessor.submitJob(it) }

    // Wait for job results
    jobResults.forEach { println(it.await()) }

    // Shutdown the processor
    microBatchingProcessor.shutdown()
}

```
## Example code
A prebuilt file containing all dependencies can be [downloaded here](https://www.dropbox.com/s/m5ifzvgeb31vhlj/microbatch-1.0-SNAPSHOT-standalone.jar?e=1&dl=1) and run on any device with a [Java runtime](https://www.java.com/download/manual.jsp) using the following command:
```
> java -jar microbatch-1.0-SNAPSHOT-standalone.jar 
> Some random numbers between 0 and 100 are: 0, 19, 85, 89, 52
```
This example uses the library to make requests to a third party random-number-generating REST webservice and displays the results.

## Design

### Coroutines for asynchronous processing

The library uses [Coroutines](https://kotlinlang.org/docs/coroutines-overview.html) for asynchronous processing, allowing jobs to be submitted and processed
concurrently. This design decision was made to ensure performance and scalability. Coroutines are lightweight and 
efficient compared to using threads directly, and run on top of a dedicated thread-pool.

Coroutine techniques used by the library include:

* Use of `launch` and `async` blocks to run code in separate child coroutines.
* Returning `CompletableDeferred` promises from job submissions to allow clients to `await()` asynchronous results.
* Safely stopping the `BatchProcessor` with the  `cancelAndJoin()` function.

### Thread-safe shared-state access
The library uses thread-safe data structures and synchronization mechanisms to ensure safe access to shared state, 
specifically the job queue using a `LinkedBlockingQueue` and the `isRunning` flag, using `AtomicBoolean`.

### Error handling 
The library offers a flexible choice of error handling. Either or both of the following approaches can be taken:

* Each `Job` handles exceptions internally, returning a `JobResult` with `success=false` when an error occurs.
* The `BatchProcessor` implementation catches Exceptions thrown when calling `execute()` on a `Job` and transforms the Exception into a failed `JobResult`.

In the second case the processor may choose to stop processing in the event of one or many exceptional conditions, or continue with
the rest of the batch.


## Tests
A suite of unit tests is included to ensure correctness and reliability of the batching processes and validation of inputs.
An example integration test which makes  network requests is also supplied.

To run the tests:
```
./gradlew test
```

## Future considerations

- Reduce time to run unit tests by using [Virtual Time](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-test/) offered by the Kotlin Coroutine testing library.
- Introduce observable state (e.g. running, idle, queue size, jobs in progress).
- Specify maximum queue size and reject jobs once this limit is reached.
- Support custom `JobResult` implementations.
- Add integration points for logging, metrics and telemetry to observe performance and resolve issues in production quickly.
- Package for deployment to a central library such as Maven.
- Support alternative batching strategies - e.g. run immediately when batch size reached.
- Support alternative `JobResult` handling such as a reactive stream that can be subscribed to.