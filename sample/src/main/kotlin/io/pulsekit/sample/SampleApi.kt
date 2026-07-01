package io.pulsekit.sample

import io.pulsekit.network.PulseOkHttpInterceptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Tiny demo API client. The only PulseKit integration is the one interceptor line —
 * every call it makes then shows up in the dashboard's **API Requests** panel.
 */
object SampleApi {

    private val client = OkHttpClient.Builder()
        .addInterceptor(PulseOkHttpInterceptor())
        .build()

    private const val BASE = "https://jsonplaceholder.typicode.com"

    /** Fire a few representative calls: a GET, a POST with a JSON body, and a 404. */
    suspend fun runDemoCalls() = withContext(Dispatchers.IO) {
        runCatching { get("$BASE/posts/1") }
        runCatching { postJson("$BASE/posts", """{"title":"PulseKit","body":"hello from the sample","userId":1}""") }
        runCatching { get("$BASE/this/path/does/not/exist") } // 404 → red status pill
    }

    private fun get(url: String) {
        client.newCall(Request.Builder().url(url).build()).execute().use { /* body peeked by interceptor */ }
    }

    private fun postJson(url: String, json: String) {
        val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
        client.newCall(Request.Builder().url(url).post(body).build()).execute().use { }
    }
}
