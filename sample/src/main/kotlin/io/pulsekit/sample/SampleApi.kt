package io.pulsekit.sample

import io.pulsekit.network.PulseOkHttpInterceptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Tiny demo API client hitting freely-available public endpoints. The only PulseKit
 * integration is the one interceptor line — every call then shows up in the
 * dashboard's **API Requests** panel.
 *
 * Endpoints used (all public, no key required):
 * - https://httpbin.org — echoes back the request (great for the Request/Response tabs)
 * - https://jsonplaceholder.typicode.com — fake REST API for GET/POST
 */
object SampleApi {

    private val client = OkHttpClient.Builder()
        .addInterceptor(PulseOkHttpInterceptor())
        .build()

    /** Fire a spread of representative public calls: GETs, POSTs-with-body, and a 404. */
    suspend fun runDemoCalls() = withContext(Dispatchers.IO) {
        // GET with a custom header (httpbin echoes it in the response).
        runCatching {
            get("https://httpbin.org/get?source=pulsekit") { header("X-Pulse-Demo", "true") }
        }
        // POST JSON with an Authorization header — shows body capture AND header redaction.
        runCatching {
            postJson(
                url = "https://httpbin.org/post",
                json = """{"title":"PulseKit","body":"hello from the sample","userId":1}""",
            ) { header("Authorization", "Bearer super-secret-token") }
        }
        // A classic public GET.
        runCatching { get("https://jsonplaceholder.typicode.com/todos/1") }
        // A public POST that returns the created resource.
        runCatching {
            postJson(
                url = "https://jsonplaceholder.typicode.com/posts",
                json = """{"title":"foo","body":"bar","userId":1}""",
            )
        }
        // A deliberate 404 → red status pill.
        runCatching { get("https://httpbin.org/status/404") }
    }

    private fun get(url: String, config: Request.Builder.() -> Unit = {}) {
        client.newCall(Request.Builder().url(url).apply(config).build()).execute().use { /* peeked */ }
    }

    private fun postJson(url: String, json: String, config: Request.Builder.() -> Unit = {}) {
        val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
        client.newCall(Request.Builder().url(url).post(body).apply(config).build()).execute().use { }
    }
}
