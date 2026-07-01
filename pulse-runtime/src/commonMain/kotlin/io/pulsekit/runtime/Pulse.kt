package io.pulsekit.runtime

import io.github.aakira.napier.Napier
import io.pulsekit.core.CustomEvent
import io.pulsekit.core.EventBus
import io.pulsekit.core.PulseConfig
import io.pulsekit.core.PulseDispatchers
import io.pulsekit.core.PulseEvent
import io.pulsekit.core.SessionId
import io.pulsekit.core.asReadOnly
import io.pulsekit.plugin.PulsePlugin
import kotlinx.coroutines.flow.Flow

/**
 * The public entry point to PulseKit.
 *
 * This surface is stable from `0.1.0` and additive-only within a major version.
 *
 * Note: initialization is expected on the main thread during app startup; a
 * dedicated concurrency guard will be introduced alongside the DI graph.
 */
object Pulse {

    private var config: PulseConfig = PulseConfig()
    private var bus: EventBus = EventBus()
    private var pluginManager: PluginManager? = null
    private var currentSession: SessionId? = null
    private var initialized: Boolean = false

    /** Read-only stream of all events for embedding UI or custom tooling. */
    val events: Flow<PulseEvent> get() = bus.events

    /**
     * Initialize PulseKit. Idempotent — a second call is a logged no-op.
     *
     * @param context platform handle (an Android `Context`, or a placeholder elsewhere).
     * @param dispatchers overridable for testing.
     */
    fun initialize(
        context: PlatformContext,
        dispatchers: PulseDispatchers = PulseDispatchers.Default,
        configure: PulseConfig.() -> Unit = {},
    ) {
        if (initialized) {
            Napier.w { "Pulse.initialize() called more than once — ignoring." }
            return
        }
        config = PulseConfig().apply(configure)
        bus = EventBus()
        pluginManager = PluginManager(
            dispatchers = dispatchers,
            eventSink = bus,
            config = config.asReadOnly(),
            onError = { name, e -> Napier.e(throwable = e) { "Plugin '$name' failed; disabled." } },
        )
        initialized = true
        startSession()
    }

    /** Start a new profiling session and return its id. */
    fun startSession(): SessionId {
        val id = SessionId("session-${nowMs()}")
        currentSession = id
        return id
    }

    /** End the current session. */
    fun endSession() {
        currentSession = null
    }

    /** Emit a custom event into the active session. */
    fun track(name: String, attributes: Map<String, String> = emptyMap()) {
        val session = currentSession ?: return
        bus.emit(
            CustomEvent(
                timestampMs = nowMs(),
                sessionId = session.value,
                name = name,
                attributes = attributes,
            ),
        )
    }

    /** Register a custom feature plugin at runtime. */
    fun registerPlugin(plugin: PulsePlugin) {
        pluginManager?.register(plugin)
            ?: Napier.w { "registerPlugin() before initialize() — ignored." }
    }

    /** Tear down all plugins and release resources. */
    fun shutdown() {
        pluginManager?.shutdownAll()
        pluginManager = null
        currentSession = null
        initialized = false
    }

    /** @return the currently active session, or null when none is running. */
    fun activeSession(): SessionId? = currentSession
}
