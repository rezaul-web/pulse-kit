package io.pulsekit.plugin

import io.pulsekit.core.EventSink
import io.pulsekit.core.PulseDispatchers
import io.pulsekit.core.ReadOnlyConfig
import kotlinx.coroutines.CoroutineScope

/**
 * The contract every PulseKit feature implements.
 *
 * The core knows only this interface — never a concrete feature — which keeps
 * features fully independent and independently publishable.
 */
interface PulsePlugin {
    /** Stable, human-readable identifier, e.g. `"fps"`. */
    val name: String

    /** Plugin version, independent of the SDK version. */
    val version: String get() = "0.1.0"

    /** Called once when the plugin is registered and enabled. Must not block. */
    fun initialize(scope: PluginScope)

    /** Called on shutdown or when disabled. Must release every resource. */
    fun shutdown()
}

/**
 * The capabilities a plugin is allowed to touch.
 *
 * Deliberately narrow: a plugin can emit events, read config, log, and launch
 * supervised coroutines — but has no direct handle on the core internals.
 */
interface PluginScope {
    val dispatchers: PulseDispatchers
    val eventSink: EventSink
    val config: ReadOnlyConfig

    /**
     * Launch a coroutine bound to the plugin's lifecycle. The coroutine is
     * cancelled automatically on [PulsePlugin.shutdown], and a failure in it
     * never propagates to the host application.
     */
    fun launch(block: suspend CoroutineScope.() -> Unit)
}
