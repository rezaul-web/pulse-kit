package io.pulsekit.core

/**
 * User-facing configuration, populated via the `Pulse.initialize { }` DSL.
 *
 * Defaults favour a zero-config, debug-only setup. This surface is part of the
 * stable public API from `0.1.0`.
 */
class PulseConfig {
    /** When true, the SDK is a no-op in non-debuggable builds. */
    var debugOnly: Boolean = true

    var enableFPS: Boolean = true
    var enableMemory: Boolean = true
    var enableCompose: Boolean = true
    var enableNetwork: Boolean = true
    var enableStartup: Boolean = true
    var enableBattery: Boolean = false
    var enableAnr: Boolean = true
    var enableLogs: Boolean = true

    /** Android floating debug overlay. */
    var overlayEnabled: Boolean = true

    /** Number of past sessions retained in storage before pruning. */
    var maxSessionsRetained: Int = 20

    /** Sampling cadence for periodic collectors (e.g. memory). */
    var samplingIntervalMs: Long = 1_000
}

/** Read-only projection of [PulseConfig] handed to plugins. */
interface ReadOnlyConfig {
    val enableFPS: Boolean
    val enableMemory: Boolean
    val enableNetwork: Boolean
    val samplingIntervalMs: Long
}

/** Adapts a mutable [PulseConfig] into the read-only view exposed to plugins. */
fun PulseConfig.asReadOnly(): ReadOnlyConfig = object : ReadOnlyConfig {
    override val enableFPS get() = this@asReadOnly.enableFPS
    override val enableMemory get() = this@asReadOnly.enableMemory
    override val enableNetwork get() = this@asReadOnly.enableNetwork
    override val samplingIntervalMs get() = this@asReadOnly.samplingIntervalMs
}
