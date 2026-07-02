package io.pulsekit.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

/** Recomposition count for one tagged composable (for the Recompositions panel). */
@Serializable
data class RecompositionStat(
    val tag: String,
    val count: Long,
    val lastTimeMs: Long,
)

/**
 * Accumulates per-tag recomposition counts reported by `Modifier.pulseRecomposeHeatmap`.
 *
 * Updated on the main thread (recomposition/draw happens there), so the backing map
 * needs no synchronization. The published list is sorted hottest-first.
 */
class RecompositionRecorder {
    private val counts = LinkedHashMap<String, RecompositionStat>()
    private val _stats = MutableStateFlow<List<RecompositionStat>>(emptyList())
    val stats: StateFlow<List<RecompositionStat>> = _stats.asStateFlow()

    fun record(tag: String, nowMs: Long) {
        val previous = counts[tag]?.count ?: 0L
        counts[tag] = RecompositionStat(tag, previous + 1, nowMs)
        _stats.value = counts.values.sortedByDescending { it.count }
    }

    fun clear() {
        counts.clear()
        _stats.value = emptyList()
    }
}
