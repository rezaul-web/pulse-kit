package io.pulsekit.android

import android.content.Context
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

        Pulse.initialize(context.applicationContext, configure = configure)

        // Register Android feature plugins. In later phases this is driven by the
        // config flags through the DI graph; kept explicit here for clarity.
        Pulse.registerPlugin(FpsPlugin(sessionIdProvider = { Pulse.activeSession()?.value }))
    }
}
