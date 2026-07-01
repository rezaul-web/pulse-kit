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

/** Opens the detail screen for the panel with [panelId]. */
@Serializable
data class PanelKey(val panelId: String) : NavKey
