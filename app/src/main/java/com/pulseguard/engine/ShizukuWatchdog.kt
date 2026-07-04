package com.pulseguard.engine

import android.content.Context
import com.pulseguard.data.SettingsRepository
import com.pulseguard.shizuku.ShizukuManager
import com.pulseguard.shizuku.ShizukuStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Watches Shizuku availability and reacts to transitions:
 *  - available → unavailable (typical after a reboot): posts the "protection paused" alert.
 *  - unavailable → available: clears the alert, re-arms the alarm, and runs a tick immediately
 *    so protection resumes without waiting for the next scheduled pulse.
 *
 * It relies on [ShizukuManager]'s binder-received / binder-dead listeners for real-time changes,
 * plus a light periodic refresh as a backstop. Runs for the lifetime of the foreground service.
 */
class ShizukuWatchdog(
    private val context: Context,
    private val shizuku: ShizukuManager,
    private val settings: SettingsRepository,
    private val alarmScheduler: AlarmScheduler,
    private val onResumed: suspend () -> Unit,
) {

    fun launchIn(scope: CoroutineScope) {
        scope.launch { observeTransitions() }
        scope.launch { periodicRefresh() }
    }

    private suspend fun observeTransitions() {
        var previous: ShizukuStatus? = null
        shizuku.status.collect { status ->
            react(previous, status)
            previous = status
        }
    }

    private suspend fun react(previous: ShizukuStatus?, status: ShizukuStatus) {
        if (!settings.snapshot().engineEnabled) {
            PulseNotifications.clearShizukuDown(context)
            return
        }
        val nowReady = status == ShizukuStatus.READY
        val first = previous == null
        val wasReady = previous == ShizukuStatus.READY

        when {
            nowReady && (first || !wasReady) -> {
                PulseNotifications.clearShizukuDown(context)
                if (!first) {
                    // Genuine recovery — resume protection right away.
                    val current = settings.snapshot()
                    alarmScheduler.scheduleNext(current)
                    onResumed()
                }
            }

            !nowReady && (first || wasReady) -> {
                // Went down, or the service started up already-down (e.g. just after a reboot).
                PulseNotifications.notifyShizukuDown(context)
            }
        }
    }

    private suspend fun periodicRefresh() {
        while (true) {
            delay(PERIODIC_MS) // cancellable: exits when the service scope is cancelled
            shizuku.refresh()
        }
    }

    private companion object {
        const val PERIODIC_MS = 60_000L
    }
}
