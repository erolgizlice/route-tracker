package com.erolgizlice.routetracker.data.tracking

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.erolgizlice.routetracker.data.R

internal class TrackingNotifications(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    /** The foreground service notification. Without POST_NOTIFICATIONS it is hidden, but the service still runs. */
    fun ongoing(markerCount: Int): Notification {
        ensureChannels()
        return NotificationCompat.Builder(context, CHANNEL_TRACKING)
            .setSmallIcon(R.drawable.ic_tracking_notification)
            .setContentTitle(context.getString(R.string.tracking_notification_title))
            .setContentText(context.resources.getQuantityString(R.plurals.tracking_notification_markers, markerCount, markerCount))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openAppIntent())
            .addAction(0, context.getString(R.string.tracking_notification_stop), stopIntent())
            .build()
    }

    fun updateOngoing(markerCount: Int) {
        if (manager.areNotificationsEnabled()) {
            @Suppress("MissingPermission") // guarded by areNotificationsEnabled()
            manager.notify(ONGOING_ID, ongoing(markerCount))
        }
    }

    /** Tells the user a session ended without them stopping it, so it never ends silently. */
    fun showTrackingStopped() {
        if (!manager.areNotificationsEnabled()) return
        ensureChannels()
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_tracking_notification)
            .setContentTitle(context.getString(R.string.tracking_stopped_title))
            .setContentText(context.getString(R.string.tracking_stopped_body))
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()
        @Suppress("MissingPermission") // guarded by areNotificationsEnabled()
        manager.notify(STOPPED_ID, notification)
    }

    private fun ensureChannels() {
        manager.createNotificationChannelsCompat(
            listOf(
                NotificationChannelCompat.Builder(CHANNEL_TRACKING, NotificationManager.IMPORTANCE_LOW)
                    .setName(context.getString(R.string.tracking_channel_name))
                    .build(),
                NotificationChannelCompat.Builder(CHANNEL_ALERTS, NotificationManager.IMPORTANCE_DEFAULT)
                    .setName(context.getString(R.string.tracking_alerts_channel_name))
                    .build(),
            ),
        )
    }

    private fun openAppIntent(): PendingIntent? {
        // :data cannot reference MainActivity; the launcher intent reaches it without that dependency.
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        return PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun stopIntent(): PendingIntent = PendingIntent.getService(
        context,
        0,
        Intent(context, TrackingService::class.java).setAction(TrackingService.ACTION_STOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        const val ONGOING_ID = 1
        private const val STOPPED_ID = 2
        private const val CHANNEL_TRACKING = "tracking"
        private const val CHANNEL_ALERTS = "tracking_alerts"
    }
}
