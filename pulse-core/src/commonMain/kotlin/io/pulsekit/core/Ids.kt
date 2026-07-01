package io.pulsekit.core

import kotlin.jvm.JvmInline

/**
 * Opaque identifier for a profiling [Session].
 *
 * Modelled as a value class to keep zero runtime overhead while remaining type-safe.
 */
@JvmInline
value class SessionId(val value: String)
