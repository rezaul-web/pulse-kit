package io.pulsekit.android.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.pulsekit.android.ui.theme.PulseTheme
import io.pulsekit.core.FrameDropped
import io.pulsekit.runtime.Pulse

/**
 * The screen opened by the PulseKit notification (see [io.pulsekit.android.PulseNotification]).
 *
 * A Compose dashboard: a grid of property panels (App Info, Device, Session,
 * Events, FPS, plus Phase-3 placeholders for API Requests / Crashes / Commit
 * History), each opening a detail screen on tap. Live counters are collected
 * from the PulseKit event bus.
 *
 * This is the interim, Android-only home for the dashboard; in Phase 3 the
 * shared parts migrate to `pulse-compose-ui` (Compose Multiplatform).
 */
class PulseDashboardActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            PulseTheme {
                var stats by remember { mutableStateOf(LiveStats()) }
                LaunchedEffect(Unit) {
                    Pulse.events.collect { event ->
                        stats = stats.copy(
                            eventCount = stats.eventCount + 1,
                            lastEvent = event::class.simpleName ?: "Event",
                            droppedFrames = stats.droppedFrames + if (event is FrameDropped) 1 else 0,
                        )
                    }
                }

                val panels = buildPanels(applicationContext, stats)

                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                ) { innerPadding ->
                    PulseDashboardScreen(panels = panels, contentPadding = innerPadding)
                }
            }
        }
    }
}
