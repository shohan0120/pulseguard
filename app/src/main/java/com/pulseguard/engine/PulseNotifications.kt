package com.pulseguard.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.pulseguard.MainActivity
import com.pulseguard.R

/** Central place for notification channels and the notifications the engine posts. */
object PulseNotifications {

    const val CHANNEL_SERVICE = "pulseguard_service"
    const val CHANNEL_ALERTS = "pulseguard_alerts"
    const val CHANNEL_LATENCY = "pulseguard_latency"

    const val ID_FOREGROUND = 1001
    const val ID_ALERT = 1002
    const val ID_LATENCY = 1003
    const val ID_PROTECTION = 1004

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)

        val service = NotificationChannel(
            CHANNEL_SERVICE,
            "Keep-alive service",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "The ongoing notification while PulseGuard is protecting your apps."
            setShowBadge(false)
        }

        val alerts = NotificationChannel(
            CHANNEL_ALERTS,
            "Status alerts",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Warnings such as Shizuku becoming disconnected."
        }

        val latency = NotificationChannel(
            CHANNEL_LATENCY,
            "Latency test",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Test notifications used to measure delivery latency."
        }

        manager.createNotificationChannels(listOf(service, alerts, latency))
    }

    fun buildForegroundNotification(context: Context, contentText: String): Notification {
        return NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_pulse)
            .setContentTitle("PulseGuard is active")
            .setContentText(contentText)
            .setOngoing(true)
            .setShowWhen(false)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openAppIntent(context))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /**
     * Distinct, high-priority "protection paused" alert, posted when Shizuku goes away (typically
     * after a reboot). Tapping opens the in-app Shizuku setup wizard; a secondary action jumps
     * straight to the Shizuku app so the user can restart it.
     */
    fun notifyShizukuDown(context: Context) {
        if (!hasNotificationPermission(context)) return
        val text =
            "Shizuku isn't connected, so PulseGuard can't keep your apps awake. Tap to reopen setup, " +
                "or restart Shizuku to resume protection."
        val builder = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_pulse)
            .setContentTitle("PulseGuard paused — Shizuku needs reactivation")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(wizardIntent(context))

        shizukuLaunchIntent(context)?.let {
            builder.addAction(R.drawable.ic_pulse, "Open Shizuku", it)
        }
        builder.addAction(R.drawable.ic_pulse, "Setup", wizardIntent(context))

        NotificationManagerCompat.from(context).notify(ID_ALERT, builder.build())
    }

    fun clearShizukuDown(context: Context) {
        NotificationManagerCompat.from(context).cancel(ID_ALERT)
    }

    /**
     * Posted when the watchdog found a protection had silently lapsed (after a reboot/update) and
     * reapplied it. Informational + high-priority; tapping opens the dashboard to review.
     */
    fun notifyProtectionLapsed(context: Context, appLabels: List<String>) {
        if (!hasNotificationPermission(context)) return
        if (appLabels.isEmpty()) return
        val names = appLabels.joinToString(", ")
        val text = "PulseGuard reapplied protection for $names after a setting lapsed. Tap to review."
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_pulse)
            .setContentTitle("Protection restored")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .build()
        NotificationManagerCompat.from(context).notify(ID_PROTECTION, notification)
    }

    /** Opens the app and deep-links to the Shizuku setup wizard. */
    private fun wizardIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = "com.pulseguard.action.OPEN_WIZARD"
            putExtra(MainActivity.EXTRA_ROUTE, MainActivity.ROUTE_SHIZUKU_WIZARD)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            REQ_WIZARD,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Launches the installed Shizuku app, or null if it isn't installed. */
    private fun shizukuLaunchIntent(context: Context): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
            ?: return null
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context,
            REQ_SHIZUKU,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private const val REQ_WIZARD = 200
    private const val REQ_SHIZUKU = 201

    fun hasNotificationPermission(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()
}
