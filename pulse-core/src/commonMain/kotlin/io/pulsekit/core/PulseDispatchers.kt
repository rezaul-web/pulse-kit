package io.pulsekit.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Platform-provided dispatcher for disk & database I/O.
 *
 * `Dispatchers.IO` is not part of the common coroutines API, so it is supplied
 * per-platform via `expect`/`actual`.
 */
internal expect val ioDispatcher: CoroutineDispatcher

/**
 * Central, injectable source of coroutine dispatchers.
 *
 * Tests substitute a deterministic implementation; production uses [Default].
 * PulseKit never references [Dispatchers] directly outside this type.
 */
interface PulseDispatchers {
    /** UI / main thread. */
    val main: CoroutineDispatcher

    /** Disk & database I/O. */
    val io: CoroutineDispatcher

    /** CPU-bound aggregation and analytics. */
    val default: CoroutineDispatcher

    companion object {
        /** Production dispatchers backed by [kotlinx.coroutines.Dispatchers]. */
        val Default: PulseDispatchers = object : PulseDispatchers {
            override val main get() = Dispatchers.Main
            override val io get() = ioDispatcher
            override val default get() = Dispatchers.Default
        }
    }
}
