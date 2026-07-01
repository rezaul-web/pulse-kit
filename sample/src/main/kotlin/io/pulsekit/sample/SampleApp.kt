package io.pulsekit.sample

import android.app.Application
import io.pulsekit.android.PulseAndroid

/** Sample application that bootstraps PulseKit with the default (debug-only) config. */
class SampleApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PulseAndroid.initialize(this) {
            enableBattery = true
        }
    }
}
