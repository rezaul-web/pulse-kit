package io.pulsekit.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

/** One phase of the startup waterfall. */
@Serializable
data class StartupPhase(val name: String, val durationMs: Long)

/**
 * App cold-start timeline (all values are `uptimeMillis`). The panel renders the
 * derived [phases] as a waterfall and shows [totalMs] time-to-first-frame.
 */
@Serializable
data class StartupMetric(
    val processStartUptimeMs: Long,
    val appOnCreateUptimeMs: Long,
    val firstActivityUptimeMs: Long,
    val firstFrameUptimeMs: Long,
) {
    val totalMs: Long get() = firstFrameUptimeMs - processStartUptimeMs

    val phases: List<StartupPhase>
        get() = listOf(
            StartupPhase("Process → Application.onCreate", appOnCreateUptimeMs - processStartUptimeMs),
            StartupPhase("onCreate → first activity", firstActivityUptimeMs - appOnCreateUptimeMs),
            StartupPhase("first activity → first frame", firstFrameUptimeMs - firstActivityUptimeMs),
        )
}

/** Holds the single [StartupMetric] captured once per process. */
class StartupHolder {
    private val _metric = MutableStateFlow<StartupMetric?>(null)
    val metric: StateFlow<StartupMetric?> = _metric.asStateFlow()

    fun set(metric: StartupMetric) {
        if (_metric.value == null) _metric.value = metric
    }
}
