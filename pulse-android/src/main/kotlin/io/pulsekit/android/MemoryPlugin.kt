package io.pulsekit.android

import android.os.Debug
import io.pulsekit.core.MemorySample
import io.pulsekit.plugin.PluginScope
import io.pulsekit.plugin.PulsePlugin
import io.pulsekit.runtime.Pulse
import kotlinx.coroutines.delay

/**
 * Samples JVM heap + native heap on a fixed interval and records a [MemorySample]
 * for the Memory panel. Sampling runs in the plugin's supervised scope (off the
 * frame path) and stops automatically on shutdown.
 */
class MemoryPlugin(
    private val samplingIntervalMs: Long,
) : PulsePlugin {

    override val name: String = "memory"

    private var scope: PluginScope? = null

    override fun initialize(scope: PluginScope) {
        this.scope = scope
        scope.launch {
            while (true) {
                Pulse.recordMemory(sample())
                delay(samplingIntervalMs)
            }
        }
    }

    private fun sample(): MemorySample {
        val runtime = Runtime.getRuntime()
        return MemorySample(
            timeMs = System.currentTimeMillis(),
            usedBytes = runtime.totalMemory() - runtime.freeMemory(),
            maxBytes = runtime.maxMemory(),
            nativeBytes = Debug.getNativeHeapAllocatedSize(),
        )
    }

    override fun shutdown() {
        scope = null
    }
}
