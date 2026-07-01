package io.pulsekit.runtime

/** JVM/Desktop placeholder context. */
actual abstract class PlatformContext

internal actual fun nowMs(): Long = System.currentTimeMillis()
