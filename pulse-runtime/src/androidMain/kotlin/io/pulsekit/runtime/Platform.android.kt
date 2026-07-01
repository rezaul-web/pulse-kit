package io.pulsekit.runtime

actual typealias PlatformContext = android.content.Context

internal actual fun nowMs(): Long = System.currentTimeMillis()
