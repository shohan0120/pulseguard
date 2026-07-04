package com.pulseguard.engine

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.pulseguard.PulseGuardApp
import com.pulseguard.data.PulseSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The resident foreground service. It keeps the process warm (so the Shizuku binding survives
 * between ticks) and executes the actual tick when poked by [PulseAlarmReceiver]. The recurring
 * cadence itself is owned by the exact alarm, not this service.
 */
class KeepAliveService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val app: PulseGuardApp get() = PulseGuardApp.from(this)
    private var tickJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        // Watch Shizuku for the service's lifetime: pause-notify on loss, auto-resume on return.
        val locator = app
        ShizukuWatchdog(
            context = this,
            shizuku = locator.shizukuManager,
            settings = locator.settingsRepository,
            alarmScheduler = locator.alarmScheduler,
            onResumed = { locator.pulseEngine.runTick() },
        ).launchIn(scope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForegroundService requires startForeground() within 5s — do it first, always.
        startForegroundInternal(defaultContentText())

        when (intent?.action) {
            ACTION_TICK -> runTick()
            ACTION_STOP -> {
                stopEngine()
                return START_NOT_STICKY
            }
            else -> handleStart()
        }
        return START_STICKY
    }

    private fun handleStart() {
        scope.launch {
            val settings = app.settingsRepository.snapshot()
            if (!settings.engineEnabled) {
                stopEngine()
                return@launch
            }
            app.engineStateRepository.setServiceRunning(true)
            val nextTime = app.alarmScheduler.scheduleNext(settings)
            app.engineStateRepository.setNextTickTime(nextTime)
            updateNotification(defaultContentText(settings))
        }
    }

    private fun runTick() {
        if (tickJob?.isActive == true) return
        tickJob = scope.launch {
            val wakeLock = acquireWakeLock()
            try {
                val settings = app.settingsRepository.snapshot()
                if (!settings.engineEnabled) {
                    stopEngine()
                    return@launch
                }
                app.engineStateRepository.setServiceRunning(true)
                val outcome = app.pulseEngine.runTick()
                updateNotification(tickContentText(settings, outcome))
            } catch (t: Throwable) {
                Log.e(TAG, "Tick failed", t)
            } finally {
                if (wakeLock.isHeld) wakeLock.release()
            }
        }
    }

    private fun stopEngine() {
        scope.launch { app.engineStateRepository.setServiceRunning(false) }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startForegroundInternal(text: String) {
        val notification = PulseNotifications.buildForegroundNotification(this, text)
        val type = if (Build.VERSION.SDK_INT >= 34) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, PulseNotifications.ID_FOREGROUND, notification, type)
    }

    private fun updateNotification(text: String) {
        // Re-post the ongoing notification with fresh content (same id).
        startForegroundInternal(text)
    }

    private fun acquireWakeLock(): PowerManager.WakeLock {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        return pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PulseGuard:tick").apply {
            setReferenceCounted(false)
            acquire(WAKELOCK_TIMEOUT_MS)
        }
    }

    private fun defaultContentText(settings: PulseSettings? = null): String {
        val count = settings?.selectedPackages?.size ?: 0
        val interval = settings?.intervalMinutes ?: PulseSettings.DEFAULT_INTERVAL_MINUTES
        return if (settings == null) {
            "Standing guard over your apps"
        } else {
            "Protecting $count app${if (count == 1) "" else "s"} · every $interval min"
        }
    }

    private fun tickContentText(settings: PulseSettings, outcome: TickOutcome): String {
        val time = timeFormat.format(Date(outcome.timestamp))
        return when {
            outcome.skipped -> "Skipped at $time (${outcome.skipReason})"
            outcome.failedPackages.isEmpty() ->
                "Pulsed ${outcome.pulsedPackages.size} app${if (outcome.pulsedPackages.size == 1) "" else "s"} at $time"
            else ->
                "Pulsed ${outcome.pulsedPackages.size}, ${outcome.failedPackages.size} failed at $time"
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "KeepAliveService"
        private const val WAKELOCK_TIMEOUT_MS = 45_000L

        const val ACTION_START = "com.pulseguard.action.START"
        const val ACTION_STOP = "com.pulseguard.action.STOP"
        const val ACTION_TICK = "com.pulseguard.action.TICK"

        private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        fun start(context: Context) {
            val intent = Intent(context, KeepAliveService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            // stopService is always permitted (no background-FGS-start restriction) and is a
            // no-op if the service isn't running — safer than starting an FGS just to stop it.
            context.stopService(Intent(context, KeepAliveService::class.java))
        }

        fun tick(context: Context) {
            val intent = Intent(context, KeepAliveService::class.java).setAction(ACTION_TICK)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
