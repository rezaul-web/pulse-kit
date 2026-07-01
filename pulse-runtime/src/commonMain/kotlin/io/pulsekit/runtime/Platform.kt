package io.pulsekit.runtime

/**
 * Platform handle passed to [Pulse.initialize].
 *
 * On Android this is `android.content.Context`; on other platforms it is a
 * lightweight placeholder so the common API stays identical everywhere.
 */
expect abstract class PlatformContext

/** Wall-clock time in epoch milliseconds. */
internal expect fun nowMs(): Long
