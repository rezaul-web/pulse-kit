package io.pulsekit.android

import android.view.Choreographer
import io.pulsekit.core.FrameDropped
import io.pulsekit.plugin.PluginScope
import io.pulsekit.plugin.PulsePlugin

/**
 * Minimal Choreographer-driven FPS collector.
 *
 * Emits a [FrameDropped] event whenever a frame exceeds the 16.67 ms budget.
 * This is a Phase-1 skeleton; jank-percentage and worst-frame aggregation are
 * added in Phase 3 per the architecture spec.
 */
class FpsPlugin(
    private val sessionIdProvider: () -> String?,
) : PulsePlugin, Choreographer.FrameCallback {

    override val name: String = "fps"

    private var scope: PluginScope? = null
    private var lastFrameTimeNanos: Long = 0L

    private companion object {
        const val FRAME_BUDGET_MS = 1000.0 / 60.0
    }

    override fun initialize(scope: PluginScope) {
        this.scope = scope
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        val previous = lastFrameTimeNanos
        lastFrameTimeNanos = frameTimeNanos
        if (previous != 0L) {
            val frameMs = (frameTimeNanos - previous) / 1_000_000.0
            if (frameMs > FRAME_BUDGET_MS) {
                val sink = scope?.eventSink
                val session = sessionIdProvider()
                if (sink != null && session != null) {
                    sink.emit(
                        FrameDropped(
                            timestampMs = frameTimeNanos / 1_000_000,
                            sessionId = session,
                            frameDurationMs = frameMs,
                            droppedFrames = (frameMs / FRAME_BUDGET_MS).toInt(),
                        ),
                    )
                }
            }
        }
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun shutdown() {
        Choreographer.getInstance().removeFrameCallback(this)
        scope = null
    }
}
