package io.pulsekit.runtime

import io.github.aakira.napier.Napier
import io.pulsekit.core.BuildProvenance
import io.pulsekit.core.CrashRecorder
import io.pulsekit.core.CrashReport
import io.pulsekit.core.CustomEvent
import io.pulsekit.core.EventBus
import io.pulsekit.core.FpsSnapshot
import io.pulsekit.core.FrameAggregator
import io.pulsekit.core.InMemoryCrashRecorder
import io.pulsekit.core.InMemoryNetworkRecorder
import io.pulsekit.core.MemoryRecorder
import io.pulsekit.core.MemorySample
import io.pulsekit.core.NetworkRecorder
import io.pulsekit.core.NetworkTransaction
import io.pulsekit.core.PulseConfig
import io.pulsekit.core.StartupHolder
import io.pulsekit.core.StartupMetric
import io.pulsekit.core.PulseDispatchers
import io.pulsekit.core.PulseEvent
import io.pulsekit.core.SessionId
import io.pulsekit.core.asReadOnly
import io.pulsekit.plugin.PulsePlugin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

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
    private var networkRecorder: NetworkRecorder = InMemoryNetworkRecorder()
    private var crashRecorder: CrashRecorder = InMemoryCrashRecorder()
    private var frameAggregator: FrameAggregator = FrameAggregator()
    private var memoryRecorder: MemoryRecorder = MemoryRecorder()
    private val startupHolder: StartupHolder = StartupHolder()

    /** Read-only stream of all events for embedding UI or custom tooling. */
    val events: Flow<PulseEvent> get() = bus.events

    /** Read-only stream of captured HTTP transactions (see `PulseOkHttpInterceptor`). */
    val network: StateFlow<List<NetworkTransaction>> get() = networkRecorder.transactions

    /** Read-only stream of captured crashes / handled exceptions. */
    val crashes: StateFlow<List<CrashReport>> get() = crashRecorder.crashes

    /** Build-time git/build provenance of the running binary (see `ARCHITECTURE.md` §6.5). */
    var provenance: BuildProvenance = BuildProvenance.EMPTY
        private set

    /** Throttled frame-timing stats (FPS/Jank panel). */
    val fps: StateFlow<FpsSnapshot> get() = frameAggregator.snapshot

    /** Rolling window of memory samples (Memory panel). */
    val memory: StateFlow<List<MemorySample>> get() = memoryRecorder.samples

    /** Cold-start timeline, captured once per process (Startup panel). */
    val startup: StateFlow<StartupMetric?> get() = startupHolder.metric

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
        networkRecorder = InMemoryNetworkRecorder()
        crashRecorder = InMemoryCrashRecorder()
        frameAggregator = FrameAggregator()
        memoryRecorder = MemoryRecorder()
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

    /**
     * Record a captured HTTP transaction. Called by `PulseOkHttpInterceptor`;
     * safe to call from any thread (and a no-op-safe before initialize).
     */
    fun recordNetwork(transaction: NetworkTransaction) {
        networkRecorder.record(transaction)
    }

    /** Record a crash / handled exception. Safe to call from any thread. */
    fun recordCrash(report: CrashReport) {
        crashRecorder.record(report)
    }

    /** Seed the crash list from persisted storage (called on startup). */
    fun restoreCrashes(reports: List<CrashReport>) {
        crashRecorder.restore(reports)
    }

    /** Set the build provenance read from the generated resource (see `ARCHITECTURE.md` §6.5). */
    fun setProvenance(value: BuildProvenance) {
        provenance = value
    }

    /** Record one frame's duration (ms). Allocation-free; call from the frame callback. */
    fun recordFrame(frameMs: Double) {
        frameAggregator.record(frameMs)
    }

    /** Recompute + emit the FPS snapshot. Call on a throttled timer, not per frame. */
    fun publishFrameStats() {
        frameAggregator.publish()
    }

    /** Record a periodic memory sample. */
    fun recordMemory(sample: MemorySample) {
        memoryRecorder.record(sample)
    }

    /** Set the cold-start timeline (captured once per process). */
    fun setStartup(metric: StartupMetric) {
        startupHolder.set(metric)
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
        networkRecorder.clear()
        crashRecorder.clear()
        currentSession = null
        initialized = false
    }

    /** @return the currently active session, or null when none is running. */
    fun activeSession(): SessionId? = currentSession
}
