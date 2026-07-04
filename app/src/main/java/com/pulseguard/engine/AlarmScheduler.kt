package com.pulseguard.engine

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.pulseguard.data.PulseSettings

/**
 * Owns the single self-rescheduling exact alarm that drives ticks. We deliberately avoid
 * periodic WorkManager (15-min floor) and instead re-arm [AlarmManager.setExactAndAllowWhileIdle]
 * on every fire, which survives Doze.
 */
class AlarmScheduler(context: Context) {

    private val appContext = context.applicationContext
    private val alarmManager =
        appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val probe = DeviceStateProbe(appContext)

    /** True when the OS will honour our exact alarms. */
    fun canScheduleExact(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

    /**
     * Arms the next alarm and returns the wall-clock time (epoch millis) it should fire, for
     * display. Uses the elapsed-realtime clock so device time changes can't skew the cadence.
     */
    fun scheduleNext(settings: PulseSettings): Long {
        val intervalMs = effectiveIntervalMinutes(settings) * 60_000L
        return scheduleIn(intervalMs)
    }

    /**
     * Arms the first alarm shortly after boot. We deliberately do NOT start the foreground
     * service directly from BOOT_COMPLETED (newer Android restricts which FGS types may launch
     * from boot); instead this soon-firing alarm starts it via the exact-alarm FGS exemption.
     */
    fun scheduleAfterBoot(delayMs: Long = 15_000L): Long = scheduleIn(delayMs)

    private fun scheduleIn(delayMs: Long): Long {
        val triggerElapsed = SystemClock.elapsedRealtime() + delayMs
        val pendingIntent = tickPendingIntent()
        try {
            if (canScheduleExact()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerElapsed,
                    pendingIntent,
                )
            } else {
                // Degrade to inexact-but-doze-tolerant if exact isn't permitted.
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerElapsed,
                    pendingIntent,
                )
            }
        } catch (se: SecurityException) {
            Log.w(TAG, "Exact alarm denied, falling back to inexact", se)
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerElapsed,
                pendingIntent,
            )
        }
        return System.currentTimeMillis() + delayMs
    }

    fun cancel() {
        alarmManager.cancel(tickPendingIntent())
    }

    /** Base interval, doubled during the configured night window when backoff is on. */
    fun effectiveIntervalMinutes(settings: PulseSettings): Int {
        val base = settings.intervalMinutes
        val night =
            settings.nightBackoffEnabled &&
                probe.isNight(settings.nightStartHour, settings.nightEndHour)
        return if (night) base * 2 else base
    }

    private fun tickPendingIntent(): PendingIntent {
        val intent = Intent(appContext, PulseAlarmReceiver::class.java).apply {
            action = ACTION_TICK
        }
        return PendingIntent.getBroadcast(
            appContext,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val TAG = "AlarmScheduler"
        const val REQUEST_CODE = 7710
        const val ACTION_TICK = "com.pulseguard.action.TICK"
    }
}
