package io.pulsekit.android

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import io.pulsekit.core.StartupMetric
import io.pulsekit.runtime.Pulse

/**
 * Captures the cold-start timeline (all in `uptimeMillis`): process start →
 * `Application.onCreate` → first activity resumed → first frame drawn.
 *
 * Call from [PulseAndroid.initialize] (which runs in `Application.onCreate`). The
 * first-frame time is taken from a `post` on the first resumed activity's decor view
 * — that runnable executes right after the first frame is drawn. Captured once, then
 * it unregisters itself.
 */
internal object PulseStartup {

    fun capture(context: Context) {
        val app = context.applicationContext as? Application ?: return
        val processStart = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Process.getStartUptimeMillis()
        } else {
            SystemClock.uptimeMillis()
        }
        val appOnCreate = SystemClock.uptimeMillis()

        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            private var done = false

            override fun onActivityResumed(activity: Activity) {
                if (done) return
                val firstActivity = SystemClock.uptimeMillis()
                val callbacks = this
                activity.window.decorView.post {
                    if (done) return@post
                    done = true
                    Pulse.setStartup(
                        StartupMetric(
                            processStartUptimeMs = processStart,
                            appOnCreateUptimeMs = appOnCreate,
                            firstActivityUptimeMs = firstActivity,
                            firstFrameUptimeMs = SystemClock.uptimeMillis(),
                        ),
                    )
                    app.unregisterActivityLifecycleCallbacks(callbacks)
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}
