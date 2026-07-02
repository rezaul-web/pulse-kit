package io.pulsekit.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable

/** One periodic memory sample. */
@Serializable
data class MemorySample(
    val timeMs: Long,
    val usedBytes: Long,
    val maxBytes: Long,
    val nativeBytes: Long = 0,
)

/** Rolling window of [MemorySample]s (oldest→newest) for the Memory panel. */
class MemoryRecorder(private val window: Int = 60) {
    private val _samples = MutableStateFlow<List<MemorySample>>(emptyList())
    val samples: StateFlow<List<MemorySample>> = _samples.asStateFlow()

    fun record(sample: MemorySample) {
        _samples.update { current -> (current + sample).takeLast(window) }
    }

    fun clear() {
        _samples.value = emptyList()
    }
}
