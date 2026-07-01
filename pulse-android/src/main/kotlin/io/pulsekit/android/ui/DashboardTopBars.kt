package io.pulsekit.android.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.pulsekit.android.R

/**
 * Top bars for the dashboard, kept in their own file so the screen composables
 * stay focused on layout. Two bars:
 *
 * - [DashboardTopBar] — the collapsing [LargeTopAppBar] shown over the panel grid.
 * - [DetailTopBar]    — the compact [TopAppBar] shown over a panel's detail.
 *
 * Both use only `material-icons-core` icons (no `material-icons-extended`
 * dependency) to keep the library lean.
 */

/**
 * Large, collapsing app bar for the grid: logo, title + subtitle, and two
 * actions — reset the live counters and close the dashboard.
 *
 * @param scrollBehavior drives the collapse; wire the same instance into the
 *   hosting `Scaffold`'s `nestedScroll` modifier.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardTopBar(
    scrollBehavior: TopAppBarScrollBehavior,
    onResetCounters: () -> Unit,
    onClose: () -> Unit,
) {
    LargeTopAppBar(
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.largeTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        navigationIcon = { LogoBadge() },
        title = {
            Column {
                Text(
                    text = "PulseKit",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Profiler dashboard",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        actions = {
            IconButton(onClick = onResetCounters) {
                Icon(Icons.Filled.Refresh, contentDescription = "Reset counters")
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Close dashboard")
            }
        },
    )
}

/**
 * Compact app bar for a panel's detail screen: a back button, the panel title,
 * and (when the panel has data) an overflow menu to copy its properties.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailTopBar(panel: PulsePanel, onBack: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var menuOpen by remember { mutableStateOf(false) }

    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
            actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        title = {
            Text(
                text = panel.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        },
        actions = {
            if (panel.properties.isNotEmpty()) {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Copy properties") },
                        onClick = {
                            menuOpen = false
                            clipboard.setText(AnnotatedString(panel.properties.asClipboardText()))
                        },
                    )
                }
            }
        },
    )
}

/** Small square PulseKit logo used as the grid bar's navigation icon. */
@Composable
private fun LogoBadge() {
    Image(
        painter = painterResource(R.drawable.ic_pulse_dashboard),
        contentDescription = null,
        modifier = Modifier
            .padding(start = 8.dp)
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp)),
    )
}

/** Render a panel's properties as plain `label: value` lines for the clipboard. */
private fun List<PulseProperty>.asClipboardText(): String =
    joinToString(separator = "\n") { "${it.label}: ${it.value}" }
