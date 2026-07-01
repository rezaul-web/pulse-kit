package io.pulsekit.network

import io.pulsekit.core.NetworkHeader
import io.pulsekit.core.NetworkTransaction
import io.pulsekit.runtime.Pulse
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

/**
 * OkHttp interceptor that records every request/response into PulseKit's network
 * store, surfaced in the dashboard's **API Requests** panel.
 *
 * Add it to your client — that's the whole integration:
 * ```
 * OkHttpClient.Builder().addInterceptor(PulseOkHttpInterceptor()).build()
 * ```
 *
 * It only observes: it reads the request body from a copy and *peeks* the response
 * body (never consuming it), so the app's traffic is unaffected. Bodies are captured
 * up to [maxBodyBytes]; sensitive headers in [redactHeaders] are masked at capture.
 *
 * @param maxBodyBytes per-body capture cap (default 256 KB).
 * @param redactHeaders header names whose values are replaced with `██` (case-insensitive).
 */
class PulseOkHttpInterceptor(
    private val maxBodyBytes: Long = 256L * 1024L,
    private val redactHeaders: Set<String> = DEFAULT_REDACTED,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val startedAtMs = System.currentTimeMillis()
        val startNs = System.nanoTime()
        val requestBody = readRequestBody(request)

        val response: Response
        try {
            response = chain.proceed(request)
        } catch (e: IOException) {
            Pulse.recordNetwork(
                failedTransaction(request, requestBody, startedAtMs, elapsedMs(startNs), e),
            )
            throw e
        }

        val durationMs = elapsedMs(startNs)
        val responseBody = readResponseBody(response)
        Pulse.recordNetwork(
            transaction(request, requestBody, response, responseBody, startedAtMs, durationMs),
        )
        return response
    }

    private fun transaction(
        request: Request,
        requestBody: String?,
        response: Response,
        responseBody: String?,
        startedAtMs: Long,
        durationMs: Long,
    ) = NetworkTransaction(
        id = nextId(startedAtMs),
        method = request.method,
        url = request.url.toString(),
        requestHeaders = request.headers.redacted(),
        requestContentType = request.body?.contentType()?.toString(),
        requestBody = requestBody,
        requestBodyBytes = request.body?.contentLengthOrZero() ?: 0L,
        statusCode = response.code,
        responseMessage = response.message,
        responseHeaders = response.headers.redacted(),
        responseContentType = response.body?.contentType()?.toString(),
        responseBody = responseBody,
        responseBodyBytes = response.body?.contentLength() ?: -1L,
        protocol = response.protocol.toString().uppercase(),
        startedAtMs = startedAtMs,
        durationMs = durationMs,
        error = null,
    )

    private fun failedTransaction(
        request: Request,
        requestBody: String?,
        startedAtMs: Long,
        durationMs: Long,
        error: Throwable,
    ) = NetworkTransaction(
        id = nextId(startedAtMs),
        method = request.method,
        url = request.url.toString(),
        requestHeaders = request.headers.redacted(),
        requestContentType = request.body?.contentType()?.toString(),
        requestBody = requestBody,
        requestBodyBytes = request.body?.contentLengthOrZero() ?: 0L,
        startedAtMs = startedAtMs,
        durationMs = durationMs,
        error = error.message ?: error::class.simpleName ?: "Request failed",
    )

    private fun readRequestBody(request: Request): String? {
        val body = request.body ?: return null
        if (body.isDuplex() || body.isOneShot()) return "<stream>"
        return try {
            val buffer = Buffer()
            body.writeTo(buffer)
            val charset = body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
            if (buffer.size > maxBodyBytes) {
                buffer.readString(maxBodyBytes, charset) + "\n… (truncated)"
            } else {
                buffer.readString(charset)
            }
        } catch (e: Exception) {
            "<unreadable request body: ${e.message}>"
        }
    }

    private fun readResponseBody(response: Response): String? = try {
        // peekBody copies up to maxBodyBytes without consuming the real stream.
        response.peekBody(maxBodyBytes).string()
    } catch (e: Exception) {
        null
    }

    private fun Headers.redacted(): List<NetworkHeader> = (0 until size).map { i ->
        val name = name(i)
        val masked = redactHeaders.any { it.equals(name, ignoreCase = true) }
        NetworkHeader(name, if (masked) "██ redacted" else value(i))
    }

    private fun okhttp3.RequestBody.contentLengthOrZero(): Long =
        try { contentLength() } catch (e: IOException) { -1L }

    private fun elapsedMs(startNs: Long): Long = (System.nanoTime() - startNs) / 1_000_000L

    private fun nextId(startedAtMs: Long): String = "$startedAtMs-${SEQ.incrementAndGet()}"

    private companion object {
        val SEQ = AtomicLong(0)
        val DEFAULT_REDACTED = setOf("Authorization", "Cookie", "Set-Cookie", "Proxy-Authorization")
    }
}
