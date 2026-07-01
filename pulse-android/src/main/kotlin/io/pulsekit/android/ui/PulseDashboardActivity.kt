package io.pulsekit.android.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.pulsekit.runtime.Pulse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * The screen opened by the PulseKit notification (see [io.pulsekit.android.PulseNotification]).
 *
 * This is a deliberately minimal, dependency-light placeholder: it proves the
 * notification → dashboard entry point end-to-end and shows a live event count.
 * The full Compose Multiplatform dashboard (App Info, API Requests, Crashes,
 * Commit History, …) arrives in Phase 3 via `pulse-compose-ui`.
 */
class PulseDashboardActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var eventCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val counter = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.parseColor("#334155"))
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F8FAFC"))
            setPadding(48, 64, 48, 48)
            addView(
                TextView(this@PulseDashboardActivity).apply {
                    text = "PulseKit"
                    textSize = 28f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(Color.parseColor("#0F172A"))
                },
            )
            addView(
                TextView(this@PulseDashboardActivity).apply {
                    text = "Profiler dashboard"
                    textSize = 14f
                    setTextColor(Color.parseColor("#64748B"))
                    setPadding(0, 8, 0, 32)
                },
            )
            addView(row("Session", Pulse.activeSession()?.value ?: "—"))
            addView(counter)
            addView(
                TextView(this@PulseDashboardActivity).apply {
                    text = "\nFull dashboard (App Info · API Requests · Crashes · " +
                        "Commit History) lands in Phase 3."
                    textSize = 13f
                    setTextColor(Color.parseColor("#94A3B8"))
                    gravity = Gravity.START
                },
            )
        }

        setContentView(ScrollView(this).apply { addView(root) })

        // Live proof-of-life: count events streaming through the bus.
        Pulse.events
            .onEach {
                eventCount++
                counter.text = "Events observed: $eventCount"
            }
            .launchIn(scope)
        counter.text = "Events observed: 0"
    }

    private fun row(label: String, value: String): TextView = TextView(this).apply {
        text = "$label: $value"
        textSize = 15f
        setTextColor(Color.parseColor("#334155"))
        setPadding(0, 8, 0, 8)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
