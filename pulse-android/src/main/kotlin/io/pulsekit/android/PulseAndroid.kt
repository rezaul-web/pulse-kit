package io.pulsekit.android

import android.content.Context
import android.content.pm.ApplicationInfo
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import io.pulsekit.core.PulseConfig
import io.pulsekit.runtime.Pulse

/**
 * Android-friendly facade over [Pulse].
 *
 * Wires the Napier logger and registers the Android feature plugins according
 * to the supplied [PulseConfig], then starts the first session.
 */
object PulseAndroid {

    /**
     * Initialize PulseKit on Android.
     *
     * @param context typically the `Application` instance.
     */
    fun initialize(context: Context, configure: PulseConfig.() -> Unit = {}) {
        Napier.base(DebugAntilog())

        val app = context.applicationContext
        Pulse.initialize(app, configure = configure)

        // Resolve the same config the runtime built so we can honour its flags here.
        val config = PulseConfig().apply(configure)

        // Register Android feature plugins per the config flags. In later phases this
        // is driven through the DI graph; kept explicit here for clarity.
        if (config.enableFPS) {
            Pulse.registerPlugin(FpsPlugin(sessionIdProvider = { Pulse.activeSession()?.value }))
        }
        if (config.enableMemory) {
            Pulse.registerPlugin(MemoryPlugin(samplingIntervalMs = config.samplingIntervalMs))
        }
        if (config.enableStartup) {
            PulseStartup.capture(app)
        }

        // Crash capture: install the chaining uncaught handler and seed from disk.
        if (config.enableCrash) {
            PulseCrashReporter.install(app)
        }
        // Commit History: load the build-time provenance resource (if generated).
        if (config.enableCommitHistory) {
            Pulse.setProvenance(PulseProvenance.load(app))
        }

        if (config.notificationEnabled && (!config.debugOnly || app.isDebuggable())) {
            PulseNotification.show(app)
        }
    }

    /**
     * Record a handled (non-fatal) exception into the Crashes panel. Fatal, uncaught
     * crashes are captured automatically once [initialize] has run.
     */
    fun recordException(throwable: Throwable) {
        PulseCrashReporter.record(throwable, fatal = false)
    }

    /**
     * (Re)post the dashboard notification.
     *
     * [initialize] posts it automatically, but that runs in `Application.onCreate`
     * — before the app can hold the Android 13+ `POST_NOTIFICATIONS` permission.
     * Call this again once the permission is granted (see the sample app) to make
     * the notification appear.
     */
    fun showDashboardNotification(context: Context) {
        PulseNotification.show(context.applicationContext)
    }

    /** True when the host app is built debuggable — the SDK's "debug build" signal. */
    private fun Context.isDebuggable(): Boolean =
        (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
}
