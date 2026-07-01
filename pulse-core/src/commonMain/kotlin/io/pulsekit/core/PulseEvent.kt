package io.pulsekit.core

import kotlinx.serialization.Serializable

/**
 * The single, immutable unit of communication across PulseKit modules.
 *
 * Every feature emits its own [PulseEvent] subtype; the core never inspects concrete
 * feature types, it only routes them through the [EventBus].
 */
@Serializable
sealed interface PulseEvent {
    /** Epoch milliseconds at which the event occurred. */
    val timestampMs: Long

    /** The session this event belongs to. */
    val sessionId: String
}

/** A screen/route became visible. */
@Serializable
data class ScreenOpened(
    override val timestampMs: Long,
    override val sessionId: String,
    val screen: String,
) : PulseEvent

/** A frame exceeded its render budget. */
@Serializable
data class FrameDropped(
    override val timestampMs: Long,
    override val sessionId: String,
    val frameDurationMs: Double,
    val droppedFrames: Int,
) : PulseEvent

/** Periodic memory sample. */
@Serializable
data class MemoryUpdated(
    override val timestampMs: Long,
    override val sessionId: String,
    val usedBytes: Long,
    val maxBytes: Long,
) : PulseEvent

/** A user-defined, custom event emitted via `Pulse.track`. */
@Serializable
data class CustomEvent(
    override val timestampMs: Long,
    override val sessionId: String,
    val name: String,
    val attributes: Map<String, String> = emptyMap(),
) : PulseEvent
