package io.pulsekit.runtime

/** iOS placeholder context — the common API stays identical across platforms. */
actual abstract class PlatformContext

internal actual fun nowMs(): Long = kotlin.system.getTimeMillis()
