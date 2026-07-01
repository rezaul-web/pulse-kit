package io.pulsekit.android

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.aakira.napier.Napier
import io.pulsekit.android.ui.PulseDashboardActivity

/**
 * Posts an ongoing, debug-only notification that opens the PulseKit dashboard
 * when tapped — the "open the debug menu from a notification" developer-tools UX.
 *
 * Characteristics (matching a persistent dev-tools notification):
 * - Its own low-importance channel, so it never buzzes or shows a badge.
 * - `ongoing = true` — it can't be swiped away while the app is installed.
 * - A single tap (and an explicit "Dashboard" action) deep-links into
 *   [PulseDashboardActivity] via a `PendingIntent`.
 *
 * Gated to debug builds by the caller ([PulseAndroid]); on Android 13+ it
 * silently no-ops if `POST_NOTIFICATIONS` has not been granted — the host app
 * owns that runtime prompt.
 */
internal object PulseNotification {

    private const val CHANNEL_ID = "pulsekit.dashboard"
    private const val CHANNEL_NAME = "PulseKit"
    private const val NOTIFICATION_ID = 0x50_15 // "PULSE"

    @SuppressLint("MissingPermission") // guarded by hasPostPermission() below
    fun show(context: Context) {
        createChannel(context)

        if (!hasPostPermission(context)) {
            Napier.w { "POST_NOTIFICATIONS not granted — PulseKit notification skipped." }
            return
        }

        val launch = Intent(context, PulseDashboardActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags = flags or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getActivity(context, NOTIFICATION_ID, launch, flags)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("PulseKit")
            .setContentText("Tap to open the profiler dashboard")
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .addAction(0, "Dashboard", pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        Napier.d { "PulseKit dashboard notification posted." }
    }

    /** Remove the notification (e.g. on shutdown). */
    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "PulseKit debug dashboard launcher"
                setShowBadge(false)
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun hasPostPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
}
