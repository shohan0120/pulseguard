package com.pulseguard.engine

import android.content.Context
import com.pulseguard.PulseGuardApp

/**
 * Public entry point the UI uses to turn the engine on/off and to re-apply configuration
 * changes (e.g. a new interval). Keeps the service/alarm wiring out of the view models.
 */
object EngineController {

    suspend fun enable(context: Context) {
        val app = PulseGuardApp.from(context)
        app.settingsRepository.setEngineEnabled(true)
        PulseNotifications.ensureChannels(context)
        KeepAliveService.start(context)
    }

    suspend fun disable(context: Context) {
        val app = PulseGuardApp.from(context)
        app.settingsRepository.setEngineEnabled(false)
        app.alarmScheduler.cancel()
        app.engineStateRepository.setNextTickTime(0L)
        KeepAliveService.stop(context)
    }

    /** Re-arm with the latest settings if the engine is currently on (e.g. interval changed). */
    suspend fun reapplyIfRunning(context: Context) {
        val app = PulseGuardApp.from(context)
        val settings = app.settingsRepository.snapshot()
        if (!settings.engineEnabled) return
        val nextTime = app.alarmScheduler.scheduleNext(settings)
        app.engineStateRepository.setNextTickTime(nextTime)
        KeepAliveService.start(context)
    }

    /** Run one tick immediately (used by the "Pulse now" action). */
    fun pulseNow(context: Context) {
        KeepAliveService.tick(context)
    }
}
