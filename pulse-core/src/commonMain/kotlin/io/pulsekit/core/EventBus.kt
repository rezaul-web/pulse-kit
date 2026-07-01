package io.pulsekit.core

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * A sink into which plugins publish [PulseEvent]s.
 *
 * Kept separate from [EventBus] so plugins receive a write-only capability
 * and can never subscribe to or replay the full stream.
 */
interface EventSink {
    /** Publish an event. Non-suspending and non-blocking; drops on overflow. */
    fun emit(event: PulseEvent)
}

/**
 * The single hot stream of profiling events for the process.
 *
 * Profiling data is loss-tolerant, so the buffer drops the oldest event on
 * overflow rather than ever suspending (and thus never blocks a producer such
 * as the Choreographer callback or an OkHttp interceptor).
 */
class EventBus(extraBufferCapacity: Int = 256) : EventSink {

    private val _events = MutableSharedFlow<PulseEvent>(
        replay = 0,
        extraBufferCapacity = extraBufferCapacity,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Read-only view of all published events. */
    val events: Flow<PulseEvent> = _events.asSharedFlow()

    override fun emit(event: PulseEvent) {
        _events.tryEmit(event)
    }
}
