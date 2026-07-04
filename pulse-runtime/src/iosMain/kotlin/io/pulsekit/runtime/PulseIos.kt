package io.pulsekit.runtime

/**
 * Convenience entry points for iOS/Swift callers. iOS has no
 * `android.content.Context`, so these hide the placeholder [PlatformContext].
 * Exposed to Swift as `PulseIosKt`.
 */

/** Initialize PulseKit and return the active session id. Safe to call once. */
fun startPulseOnIos(): String {
    Pulse.initialize(object : PlatformContext() {})
    return Pulse.activeSession()?.value ?: ""
}
