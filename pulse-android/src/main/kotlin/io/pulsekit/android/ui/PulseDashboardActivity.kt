package io.pulsekit.android.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.pulsekit.android.ui.theme.PulseTheme

/**
 * The screen opened by the PulseKit notification (see [io.pulsekit.android.PulseNotification]).
 *
 * Thin Compose host: it applies the theme, goes edge-to-edge, and delegates all
 * UI and state to [PulseDashboard]. The dashboard is a grid of property panels
 * (App Info, Device, Session, Events, FPS live; API Requests, Crashes, Commit
 * History as Phase-3 placeholders), each opening a detail screen on tap.
 *
 * This is the interim, Android-only home for the dashboard; in Phase 3 the shared
 * parts migrate to `pulse-compose-ui` (Compose Multiplatform). The full design is
 * documented in `docs/DASHBOARD.md`.
 */
class PulseDashboardActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            PulseTheme {
                PulseDashboard(onClose = ::finish)
            }
        }
    }
}
