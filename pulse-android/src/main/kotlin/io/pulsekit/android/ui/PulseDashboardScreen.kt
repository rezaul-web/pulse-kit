package io.pulsekit.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import io.pulsekit.runtime.Pulse

/** Layout constants for the dashboard, in one place for easy tuning. */
private object DashboardDefaults {
    val TileMinWidth = 168.dp   // drives the adaptive column count
    val ScreenPadding = 16.dp
    val GridSpacing = 12.dp
    val TilePadding = 14.dp
    val TileHeight = 116.dp
    val BadgeSize = 34.dp
    val BadgeCorner = 10.dp
    val CardCorner = 16.dp
    val ContentMaxWidth = 640.dp // keep content readable on tablets/landscape
}

/**
 * Stateful host for the whole dashboard.
 *
 * Owns the live [LiveStats] (collected from [Pulse.events]) and the **Navigation 3**
 * back stack of [androidx.navigation3.runtime.NavKey]s. [NavDisplay] renders the top
 * key: [HomeKey] → the panel grid, [PanelKey] → a panel's detail. Predictive back is
 * handled by `NavDisplay`'s `onBack`.
 *
 * @param onClose invoked by the grid's close action (the Activity passes `finish`).
 */
@Composable
fun PulseDashboard(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    // Live counters: fold each event into LiveStats via the pure reducer.
    var stats by remember { mutableStateOf(LiveStats()) }
    LaunchedEffect(Unit) {
        Pulse.events.collect { event -> stats = stats.reduce(event) }
    }
    val panels = buildPanels(context, stats)

    val backStack = rememberNavBackStack(HomeKey)

    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider<NavKey> {
            entry<HomeKey> {
                DashboardGridScreen(
                    panels = panels,
                    onOpen = { backStack.add(PanelKey(it.id)) },
                    onResetCounters = { stats = LiveStats() },
                    onClose = onClose,
                )
            }
            entry<PanelKey> { key ->
                val panel = panels.firstOrNull { it.id == key.panelId }
                if (panel != null) {
                    DashboardDetailScreen(panel = panel, onBack = { backStack.removeLastOrNull() })
                }
            }
        },
    )
}

/** Home destination: collapsing top bar over the adaptive panel grid. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardGridScreen(
    panels: List<PulsePanel>,
    onOpen: (PulsePanel) -> Unit,
    onResetCounters: () -> Unit,
    onClose: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            DashboardTopBar(
                scrollBehavior = scrollBehavior,
                onResetCounters = onResetCounters,
                onClose = onClose,
            )
        },
    ) { innerPadding ->
        PanelGrid(panels = panels, contentPadding = innerPadding, onOpen = onOpen)
    }
}

/** Detail destination: back/copy top bar over the panel's properties. */
@Composable
private fun DashboardDetailScreen(panel: PulsePanel, onBack: () -> Unit) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { DetailTopBar(panel = panel, onBack = onBack) },
    ) { innerPadding ->
        PanelDetail(panel = panel, contentPadding = innerPadding)
    }
}

/** Adaptive grid — columns reflow with available width (phone/tablet/landscape). */
@Composable
private fun PanelGrid(
    panels: List<PulsePanel>,
    contentPadding: PaddingValues,
    onOpen: (PulsePanel) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = DashboardDefaults.TileMinWidth),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = DashboardDefaults.ScreenPadding,
            end = DashboardDefaults.ScreenPadding,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(DashboardDefaults.GridSpacing),
        verticalArrangement = Arrangement.spacedBy(DashboardDefaults.GridSpacing),
    ) {
        items(panels, key = { it.id }) { panel ->
            PanelTile(panel = panel, onClick = { onOpen(panel) })
        }
    }
}

/** A single tile: badge, title, and a one-line live summary. */
@Composable
private fun PanelTile(panel: PulsePanel, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(DashboardDefaults.TileHeight)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(DashboardDefaults.CardCorner),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(DashboardDefaults.TilePadding),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Badge(text = panel.badge, muted = !panel.available)
            Column {
                Text(
                    panel.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    panel.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (panel.available) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The colored initials badge on a tile (muted for Phase-3 placeholders). */
@Composable
private fun Badge(text: String, muted: Boolean) {
    val background = if (muted) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary
    val foreground = if (muted) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.onPrimary
    Box(
        modifier = Modifier
            .size(DashboardDefaults.BadgeSize)
            .clip(RoundedCornerShape(DashboardDefaults.BadgeCorner))
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = foreground,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** A panel's detail body: property rows in a card, or a Phase-3 empty state. */
@Composable
private fun PanelDetail(panel: PulsePanel, contentPadding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding(),
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (panel.properties.isEmpty()) {
            EmptyPanel(panel)
        } else {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = DashboardDefaults.ContentMaxWidth)
                    .padding(horizontal = DashboardDefaults.ScreenPadding, vertical = 8.dp),
                shape = RoundedCornerShape(DashboardDefaults.CardCorner),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Column(Modifier.padding(horizontal = DashboardDefaults.ScreenPadding)) {
                    panel.properties.forEachIndexed { index, property ->
                        PropertyRow(property)
                        if (index != panel.properties.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }
}

/** One label/value row; values are monospaced for scannability. */
@Composable
private fun PropertyRow(property: PulseProperty) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            property.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 16.dp),
        )
        Spacer(Modifier.weight(1f))
        Text(
            property.value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Placeholder body for panels whose data arrives in Phase 3. */
@Composable
private fun EmptyPanel(panel: PulsePanel) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "${panel.title} lands in Phase 3",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "This panel is wired into the dashboard (see ARCHITECTURE.md §7.0) " +
                    "but has no data yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
