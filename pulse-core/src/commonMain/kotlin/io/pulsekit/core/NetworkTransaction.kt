package io.pulsekit.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable

/** A single HTTP header (already redaction-processed at capture time). */
@Serializable
data class NetworkHeader(val name: String, val value: String)

/**
 * An immutable record of one captured HTTP round-trip.
 *
 * Bodies are captured up to a cap and may be truncated or marked (`<stream>`,
 * `<binary>`). Sensitive headers are redacted at capture time (see the
 * interceptor), so this object is always safe to display or export.
 */
@Serializable
data class NetworkTransaction(
    val id: String,
    val method: String,
    val url: String,
    val requestHeaders: List<NetworkHeader> = emptyList(),
    val requestContentType: String? = null,
    val requestBody: String? = null,
    val requestBodyBytes: Long = 0,
    val statusCode: Int = 0,
    val responseMessage: String = "",
    val responseHeaders: List<NetworkHeader> = emptyList(),
    val responseContentType: String? = null,
    val responseBody: String? = null,
    val responseBodyBytes: Long = 0,
    val protocol: String = "",
    val startedAtMs: Long = 0,
    val durationMs: Long = 0,
    /** Non-null when the call failed before/without a response (e.g. no network). */
    val error: String? = null,
) {
    val isFailed: Boolean get() = error != null
    val isSuccessful: Boolean get() = error == null && statusCode in 200..399
    /** Just the path (+query) of [url], for compact list rows. */
    val path: String get() = url.substringAfter("://").substringAfter('/', "").let { "/$it" }
    val host: String get() = url.substringAfter("://").substringBefore('/')
}

/**
 * Render the transaction's request as a runnable `curl` command — the exact
 * request that was sent (redacted headers included as `██`).
 */
fun NetworkTransaction.toCurl(): String = buildString {
    append("curl -X ").append(method)
    for (header in requestHeaders) {
        append(" \\\n  -H '").append(header.name).append(": ").append(header.value.escapeSingleQuotes()).append("'")
    }
    if (!requestBody.isNullOrEmpty()) {
        append(" \\\n  --data '").append(requestBody.escapeSingleQuotes()).append("'")
    }
    append(" \\\n  '").append(url).append("'")
}

private fun String.escapeSingleQuotes(): String = replace("'", "'\\''")

/** Store of captured [NetworkTransaction]s, exposed to the dashboard as a stream. */
interface NetworkRecorder {
    val transactions: StateFlow<List<NetworkTransaction>>
    fun record(transaction: NetworkTransaction)
    fun clear()
}

/**
 * In-memory [NetworkRecorder] keeping the newest [maxEntries] transactions
 * (newest first). Thread-safe via atomic [update]; the SQLDelight-backed store
 * arrives in Phase 3.
 */
class InMemoryNetworkRecorder(private val maxEntries: Int = 100) : NetworkRecorder {
    private val _transactions = MutableStateFlow<List<NetworkTransaction>>(emptyList())
    override val transactions: StateFlow<List<NetworkTransaction>> = _transactions.asStateFlow()

    override fun record(transaction: NetworkTransaction) {
        _transactions.update { current -> (listOf(transaction) + current).take(maxEntries) }
    }

    override fun clear() {
        _transactions.value = emptyList()
    }
}
