package io.pulsekit.android.ui

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Navigation 3 destination keys for the dashboard.
 *
 * Each screen is identified by a [NavKey]. The back stack is a list of these keys
 * (`rememberNavBackStack`), and [androidx.navigation3.ui.NavDisplay] renders the
 * top one. Keys are `@Serializable` so the stack survives process death.
 */
@Serializable
data object HomeKey : NavKey

/** Opens the property-detail screen for the panel with [panelId]. */
@Serializable
data class PanelKey(val panelId: String) : NavKey

/** Opens the list of captured API requests. */
@Serializable
data object ApiListKey : NavKey

/** Opens the tabbed detail (cURL · Request · Response) for one captured request. */
@Serializable
data class ApiDetailKey(val transactionId: String) : NavKey

/** Opens the list of captured crashes / handled exceptions. */
@Serializable
data object CrashListKey : NavKey

/** Opens the detail (stack trace) for one crash. */
@Serializable
data class CrashDetailKey(val crashId: String) : NavKey

/** Opens the Commit History (build provenance) screen. */
@Serializable
data object CommitHistoryKey : NavKey

/** Opens the FPS / Jank metrics screen. */
@Serializable
data object FpsKey : NavKey

/** Opens the Memory metrics screen. */
@Serializable
data object MemoryKey : NavKey

/** Opens the Startup timeline screen. */
@Serializable
data object StartupKey : NavKey

/** Opens the Recompositions (Compose heatmap counts) screen. */
@Serializable
data object RecompositionsKey : NavKey
