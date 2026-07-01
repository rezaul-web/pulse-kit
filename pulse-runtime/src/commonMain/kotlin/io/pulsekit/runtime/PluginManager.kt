package io.pulsekit.runtime

import io.pulsekit.core.EventSink
import io.pulsekit.core.PulseDispatchers
import io.pulsekit.core.ReadOnlyConfig
import io.pulsekit.plugin.PluginScope
import io.pulsekit.plugin.PulsePlugin
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Registers, initializes and tears down plugins under supervised scopes.
 *
 * A failure inside any plugin is contained: it disables that plugin and is
 * logged, but never crashes the host application.
 */
internal class PluginManager(
    private val dispatchers: PulseDispatchers,
    private val eventSink: EventSink,
    private val config: ReadOnlyConfig,
    private val onError: (pluginName: String, error: Throwable) -> Unit,
) {
    private data class Registered(val plugin: PulsePlugin, val scope: CoroutineScope)

    private val registered = mutableListOf<Registered>()

    /** Registers and initializes a plugin. Isolation guarantees no crash escapes. */
    fun register(plugin: PulsePlugin) {
        val handler = CoroutineExceptionHandler { _, e -> onError(plugin.name, e) }
        val pluginScope = CoroutineScope(SupervisorJob() + dispatchers.default + handler)

        val scope = object : PluginScope {
            override val dispatchers = this@PluginManager.dispatchers
            override val eventSink = this@PluginManager.eventSink
            override val config = this@PluginManager.config
            override fun launch(block: suspend CoroutineScope.() -> Unit) {
                pluginScope.launch { block() }
            }
        }

        try {
            plugin.initialize(scope)
            registered += Registered(plugin, pluginScope)
        } catch (e: Throwable) {
            pluginScope.cancel()
            onError(plugin.name, e)
        }
    }

    /** Shuts down every plugin, isolating failures. */
    fun shutdownAll() {
        registered.forEach { (plugin, scope) ->
            try {
                plugin.shutdown()
            } catch (e: Throwable) {
                onError(plugin.name, e)
            } finally {
                scope.cancel()
            }
        }
        registered.clear()
    }
}
