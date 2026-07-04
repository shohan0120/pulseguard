package com.pulseguard.engine

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.pulseguard.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One measured round-trip of the notification pipeline. */
data class LatencyResult(
    val requestedAt: Long,
    val deliveredAt: Long,
) {
    val latencyMs: Long get() = (deliveredAt - requestedAt).coerceAtLeast(0)
}

/**
 * Measures real notification delivery latency: it schedules an exact alarm to fire "now",
 * and when the alarm's receiver runs it posts a visible notification and records how long the
 * schedule→deliver round-trip actually took. Under Doze / OEM throttling this number grows,
 * which is exactly what the user wants to see improve after setup.
 */
object LatencyTester {

    private const val REQUEST_CODE = 9911
    const val EXTRA_REQUESTED_AT = "com.pulseguard.extra.REQUESTED_AT"

    private val _results = MutableStateFlow<List<LatencyResult>>(emptyList())
    val results: StateFlow<List<LatencyResult>> = _results.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    fun runTest(context: Context) {
        val appContext = context.applicationContext
        _running.value = true
        PulseNotifications.ensureChannels(appContext)

        val requestedAt = System.currentTimeMillis()
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(appContext, LatencyAlarmReceiver::class.java)
            .putExtra(EXTRA_REQUESTED_AT, requestedAt)
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
        if (canExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, requestedAt, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, requestedAt, pendingIntent)
        }
    }

    internal fun record(context: Context, requestedAt: Long, deliveredAt: Long) {
        val result = LatencyResult(requestedAt, deliveredAt)
        _results.value = (_results.value + result).takeLast(HISTORY)
        _running.value = false
        postDeliveryNotification(context, result)
    }

    private fun postDeliveryNotification(context: Context, result: LatencyResult) {
        if (!PulseNotifications.hasNotificationPermission(context)) return
        val notification = NotificationCompat.Builder(context, PulseNotifications.CHANNEL_LATENCY)
            .setSmallIcon(R.drawable.ic_pulse)
            .setContentTitle("Test notification delivered")
            .setContentText("Round-trip latency: ${result.latencyMs} ms")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(context).notify(PulseNotifications.ID_LATENCY, notification)
    }

    private const val HISTORY = 10
}

/** Receives the latency-test alarm and reports delivery time back to [LatencyTester]. */
class LatencyAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val requestedAt = intent?.getLongExtra(LatencyTester.EXTRA_REQUESTED_AT, 0L) ?: 0L
        if (requestedAt <= 0L) return
        LatencyTester.record(context, requestedAt, System.currentTimeMillis())
    }
}
