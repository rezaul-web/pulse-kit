package io.pulsekit.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable

/**
 * An immutable record of a captured exception — fatal (an uncaught crash) or a
 * handled/non-fatal one recorded explicitly.
 */
@Serializable
data class CrashReport(
    val id: String,
    val timestampMs: Long,
    val type: String,
    val message: String,
    val threadName: String,
    val stackTrace: String,
    val fatal: Boolean,
    val sessionId: String? = null,
) {
    val title: String get() = if (message.isBlank()) type else "$type: $message"
}

/** Store of captured [CrashReport]s, exposed to the dashboard as a stream. */
interface CrashRecorder {
    val crashes: StateFlow<List<CrashReport>>
    fun record(report: CrashReport)
    /** Replace the whole list (used to seed from persisted storage on startup). */
    fun restore(reports: List<CrashReport>)
    fun clear()
}

/** In-memory [CrashRecorder] keeping the newest [maxEntries] reports (newest first). */
class InMemoryCrashRecorder(private val maxEntries: Int = 100) : CrashRecorder {
    private val _crashes = MutableStateFlow<List<CrashReport>>(emptyList())
    override val crashes: StateFlow<List<CrashReport>> = _crashes.asStateFlow()

    override fun record(report: CrashReport) {
        _crashes.update { current -> (listOf(report) + current).take(maxEntries) }
    }

    override fun restore(reports: List<CrashReport>) {
        _crashes.value = reports.take(maxEntries)
    }

    override fun clear() {
        _crashes.value = emptyList()
    }
}
