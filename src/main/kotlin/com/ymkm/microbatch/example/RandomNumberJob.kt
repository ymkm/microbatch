package com.ymkm.microbatch.example

import com.ymkm.microbatch.Job
import com.ymkm.microbatch.JobResult
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Job that runs makes an HTTP request to a third party random-number-generating website
 * and returns the result as a String
 */
class RandomNumberJob : Job {
    private val client = OkHttpClient()
    override suspend fun execute(): JobResult {
        val request = Request.Builder()
            .url("http://www.randomnumberapi.com/api/v1.0/randomnumber")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return JobResult(
                false,
                "Failed to read response from random number server"
            )

            return response.body?.string()?.let {
                JobResult(true, responseToNumber(it))
            } ?: JobResult(false, "Failed to read random number")
        }
    }

    // remove JSON formatting
    private fun responseToNumber(response: String) = response.substring(1, response.length - 2)
}