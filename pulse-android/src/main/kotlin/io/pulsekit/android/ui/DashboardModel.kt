package io.pulsekit.android.ui

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.util.DisplayMetrics
import androidx.core.content.pm.PackageInfoCompat
import io.pulsekit.core.FrameDropped
import io.pulsekit.core.PulseEvent
import io.pulsekit.runtime.Pulse
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A single property row inside a panel's detail screen. */
data class PulseProperty(val label: String, val value: String)

/**
 * A dashboard tile. [available] panels show real data now; the rest are
 * placeholders for the features that land in Phase 3 (see ARCHITECTURE.md §7.0).
 */
data class PulsePanel(
    val id: String,
    val title: String,
    val badge: String,
    val summary: String,
    val available: Boolean,
    val properties: List<PulseProperty>,
)

/** Live counters the dashboard observes from the event bus. */
data class LiveStats(
    val eventCount: Int = 0,
    val lastEvent: String = "—",
    val droppedFrames: Int = 0,
)

/**
 * Fold a single [PulseEvent] into the running [LiveStats]. Pure and total, so it
 * is trivially testable and keeps the collection site in the UI free of logic.
 */
fun LiveStats.reduce(event: PulseEvent): LiveStats = copy(
    eventCount = eventCount + 1,
    lastEvent = event::class.simpleName ?: "Event",
    droppedFrames = droppedFrames + if (event is FrameDropped) 1 else 0,
)

/**
 * Build the full panel list, folding in the current [stats] snapshot and the
 * number of captured API requests ([apiCount]).
 */
fun buildPanels(context: Context, stats: LiveStats, apiCount: Int = 0): List<PulsePanel> = listOf(
    PulsePanel(
        id = "app_info",
        title = "App Info",
        badge = "Ap",
        summary = appVersion(context),
        available = true,
        properties = appInfoProperties(context),
    ),
    PulsePanel(
        id = "device",
        title = "Device",
        badge = "Dv",
        summary = "${Build.MANUFACTURER} ${Build.MODEL}",
        available = true,
        properties = deviceProperties(context),
    ),
    PulsePanel(
        id = "session",
        title = "Session",
        badge = "Ss",
        summary = Pulse.activeSession()?.value?.removePrefix("session-")?.take(8) ?: "—",
        available = true,
        properties = listOf(
            PulseProperty("Session id", Pulse.activeSession()?.value ?: "—"),
            PulseProperty("Events observed", stats.eventCount.toString()),
            PulseProperty("Last event", stats.lastEvent),
        ),
    ),
    PulsePanel(
        id = "events",
        title = "Events",
        badge = "Ev",
        summary = "${stats.eventCount} seen",
        available = true,
        properties = listOf(
            PulseProperty("Total observed", stats.eventCount.toString()),
            PulseProperty("Most recent", stats.lastEvent),
        ),
    ),
    PulsePanel(
        id = "fps",
        title = "FPS / Jank",
        badge = "Fp",
        summary = "${stats.droppedFrames} drops",
        available = true,
        properties = listOf(
            PulseProperty("Dropped frames", stats.droppedFrames.toString()),
            PulseProperty("Collector", "Choreographer (16.67ms budget)"),
        ),
    ),
    PulsePanel(
        id = "api",
        title = "API Requests",
        badge = "Ap",
        summary = if (apiCount == 1) "1 call" else "$apiCount calls",
        available = true,
        properties = emptyList(), // opens the dedicated list screen, not property rows
    ),
    PulsePanel(
        id = "crashes",
        title = "Crashes",
        badge = "Cr",
        summary = "Phase 3",
        available = false,
        properties = emptyList(),
    ),
    PulsePanel(
        id = "commits",
        title = "Commit History",
        badge = "Gt",
        summary = "Phase 3",
        available = false,
        properties = emptyList(),
    ),
)

private fun appVersion(context: Context): String = try {
    val pi = context.packageManager.getPackageInfo(context.packageName, 0)
    "v${pi.versionName}"
} catch (_: Exception) {
    "—"
}

private fun appInfoProperties(context: Context): List<PulseProperty> {
    val pm = context.packageManager
    val pkg = context.packageName
    val ai = context.applicationInfo
    val debuggable = (ai.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    val list = mutableListOf(
        PulseProperty("Package", pkg),
        PulseProperty("Build type", if (debuggable) "debug" else "release"),
        PulseProperty("Target SDK", ai.targetSdkVersion.toString()),
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        list += PulseProperty("Min SDK", ai.minSdkVersion.toString())
    }
    try {
        val pi = pm.getPackageInfo(pkg, 0)
        list += PulseProperty("Version", "${pi.versionName} (${PackageInfoCompat.getLongVersionCode(pi)})")
        list += PulseProperty("First install", fmtTime(pi.firstInstallTime))
        list += PulseProperty("Last update", fmtTime(pi.lastUpdateTime))
    } catch (_: Exception) {
        // package always resolvable for self; ignore defensively
    }
    val installer = runCatching { pm.getInstallerPackageName(pkg) }.getOrNull()
    list += PulseProperty("Installer", installer ?: "—")
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        list += PulseProperty("Process", Application.getProcessName())
    }
    return list
}

private fun deviceProperties(context: Context): List<PulseProperty> {
    val dm: DisplayMetrics = context.resources.displayMetrics
    return listOf(
        PulseProperty("Manufacturer", Build.MANUFACTURER),
        PulseProperty("Model", Build.MODEL),
        PulseProperty("Device", Build.DEVICE),
        PulseProperty("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"),
        PulseProperty("ABI", Build.SUPPORTED_ABIS.firstOrNull() ?: "—"),
        PulseProperty("Locale", Locale.getDefault().toLanguageTag()),
        PulseProperty("Screen", "${dm.widthPixels}×${dm.heightPixels} · ${dm.densityDpi}dpi"),
    )
}

private fun fmtTime(ms: Long): String {
    if (ms <= 0L) return "—"
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(ms))
}
