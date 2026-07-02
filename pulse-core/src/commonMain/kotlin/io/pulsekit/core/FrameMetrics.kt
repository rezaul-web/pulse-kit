package io.pulsekit.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

/** A throttled snapshot of frame-timing stats for the FPS/Jank panel. */
@Serializable
data class FpsSnapshot(
    val fps: Int = 0,
    val averageFps: Int = 0,
    val worstFrameMs: Double = 0.0,
    val jankPercent: Int = 0,
    val droppedFrames: Int = 0,
    val sampleCount: Int = 0,
    /** Recent frame durations (ms), oldest→newest, for the sparkline. */
    val recentFrameMs: List<Double> = emptyList(),
) {
    companion object {
        val EMPTY = FpsSnapshot()
    }
}

/**
 * Aggregates per-frame durations into an [FpsSnapshot].
 *
 * [record] is called once per frame from the Choreographer callback and is
 * **allocation-free** (writes into a primitive ring buffer) so it adds no GC
 * pressure on the main thread. [publish] recomputes the snapshot and is called on a
 * throttled cadence (~2×/sec), where a small allocation is acceptable.
 */
class FrameAggregator(
    private val window: Int = 120,
    private val frameBudgetMs: Double = 1000.0 / 60.0,
) {
    private val ring = DoubleArray(window)
    private var index = 0
    private var count = 0
    private var totalDropped = 0

    private val _snapshot = MutableStateFlow(FpsSnapshot.EMPTY)
    val snapshot: StateFlow<FpsSnapshot> = _snapshot.asStateFlow()

    /** Record one frame's duration (ms). Allocation-free; safe on the main thread. */
    fun record(frameMs: Double) {
        ring[index] = frameMs
        index = (index + 1) % window
        if (count < window) count++
        if (frameMs > frameBudgetMs) totalDropped++
    }

    /** Recompute and emit the snapshot. Call on a throttled timer, not per frame. */
    fun publish() {
        if (count == 0) return
        val start = if (count < window) 0 else index
        var sum = 0.0
        var worst = 0.0
        var jank = 0
        val recent = ArrayList<Double>(count)
        for (i in 0 until count) {
            val v = ring[(start + i) % window]
            sum += v
            if (v > worst) worst = v
            if (v > frameBudgetMs) jank++
            recent.add(v)
        }
        val avgMs = sum / count
        val lastMs = recent.last()
        _snapshot.value = FpsSnapshot(
            fps = if (lastMs > 0) (1000.0 / lastMs).toInt().coerceIn(0, 120) else 0,
            averageFps = if (avgMs > 0) (1000.0 / avgMs).toInt().coerceIn(0, 120) else 0,
            worstFrameMs = worst,
            jankPercent = (jank * 100) / count,
            droppedFrames = totalDropped,
            sampleCount = count,
            recentFrameMs = recent,
        )
    }

    fun reset() {
        index = 0
        count = 0
        totalDropped = 0
        _snapshot.value = FpsSnapshot.EMPTY
    }
}
