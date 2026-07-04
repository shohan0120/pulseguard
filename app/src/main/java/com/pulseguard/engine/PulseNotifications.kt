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

    fun notifyShizukuDown(context: Context) {
        if (!hasNotificationPermission(context)) return
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_pulse)
            .setContentTitle("PulseGuard needs Shizuku")
            .setContentText("Shizuku is not connected. Ticks are paused until you reactivate it.")
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(context).notify(ID_ALERT, notification)
    }

    fun clearShizukuDown(context: Context) {
        NotificationManagerCompat.from(context).cancel(ID_ALERT)
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

    fun hasNotificationPermission(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()
}
