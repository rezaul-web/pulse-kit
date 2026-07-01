package io.pulsekit.android.ui

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import io.pulsekit.android.R
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
 * A deliberately minimal, dependency-light placeholder: it proves the
 * notification → dashboard entry point end-to-end and shows a live event count.
 * The full Compose Multiplatform dashboard (App Info, API Requests, Crashes,
 * Commit History, …) arrives in Phase 3 via `pulse-compose-ui`.
 *
 * Colors come from themed resources ([R.color], with dark-mode variants under
 * `values-night/`), driven by `Theme.PulseKit.Dashboard`, so the placeholder
 * respects the brand palette and the system light/dark setting.
 */
class PulseDashboardActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var eventCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val onSurface = color(R.color.pulse_on_surface)
        val onVariant = color(R.color.pulse_on_surface_variant)
        val muted = color(R.color.pulse_on_surface_muted)

        val counter = label("", sizeSp = 16f, textColor = onSurface)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(color(R.color.pulse_background))
            setPadding(dp(24), dp(32), dp(24), dp(24))
            addView(label("PulseKit", sizeSp = 28f, textColor = onSurface, bold = true))
            addView(
                label("Profiler dashboard", sizeSp = 14f, textColor = onVariant).apply {
                    setPadding(0, dp(4), 0, dp(24))
                },
            )
            addView(label("Session: ${Pulse.activeSession()?.value ?: "—"}", sizeSp = 15f, textColor = onSurface))
            addView(counter)
            addView(
                label(
                    "\nFull dashboard (App Info · API Requests · Crashes · " +
                        "Commit History) lands in Phase 3.",
                    sizeSp = 13f,
                    textColor = muted,
                ),
            )
        }

        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)
        applyInsetsPadding(root)

        // Live proof-of-life: count events streaming through the bus.
        counter.text = "Events observed: 0"
        Pulse.events
            .onEach {
                eventCount++
                counter.text = "Events observed: $eventCount"
            }
            .launchIn(scope)
    }

    private fun label(
        text: String,
        sizeSp: Float,
        textColor: Int,
        bold: Boolean = false,
    ): TextView = TextView(this).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(textColor)
        setPadding(0, dp(8), 0, dp(8))
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun color(resId: Int): Int = ContextCompat.getColor(this, resId)

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun applyInsetsPadding(target: View) {
        ViewCompat.setOnApplyWindowInsetsListener(target) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = dp(32) + bars.top, bottom = dp(24) + bars.bottom)
            insets
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
