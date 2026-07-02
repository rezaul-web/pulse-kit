package io.pulsekit.android

import android.view.Choreographer
import io.pulsekit.core.FrameDropped
import io.pulsekit.plugin.PluginScope
import io.pulsekit.plugin.PulsePlugin
import io.pulsekit.runtime.Pulse
import kotlinx.coroutines.delay

/**
 * Choreographer-driven FPS/jank collector.
 *
 * Feeds every frame's duration into the (allocation-free) [io.pulsekit.core.FrameAggregator]
 * via [Pulse.recordFrame], emits a [FrameDropped] event for frames over budget, and
 * ticks [Pulse.publishFrameStats] on a throttled timer so the panel updates ~2×/sec
 * without recomputing on the frame-critical path.
 */
class FpsPlugin(
    private val sessionIdProvider: () -> String?,
) : PulsePlugin, Choreographer.FrameCallback {

    override val name: String = "fps"

    private var scope: PluginScope? = null
    private var lastFrameTimeNanos: Long = 0L

    private companion object {
        const val FRAME_BUDGET_MS = 1000.0 / 60.0
        const val PUBLISH_INTERVAL_MS = 500L
    }

    override fun initialize(scope: PluginScope) {
        this.scope = scope
        Choreographer.getInstance().postFrameCallback(this)
        scope.launch {
            while (true) {
                delay(PUBLISH_INTERVAL_MS)
                Pulse.publishFrameStats()
            }
        }
    }

    override fun doFrame(frameTimeNanos: Long) {
        val previous = lastFrameTimeNanos
        lastFrameTimeNanos = frameTimeNanos
        if (previous != 0L) {
            val frameMs = (frameTimeNanos - previous) / 1_000_000.0
            Pulse.recordFrame(frameMs)
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
