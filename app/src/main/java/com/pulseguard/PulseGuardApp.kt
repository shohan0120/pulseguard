package com.pulseguard

import android.app.Application
import android.content.Context
import com.pulseguard.data.AppRepository
import com.pulseguard.data.SettingsRepository
import com.pulseguard.engine.AlarmScheduler
import com.pulseguard.engine.EngineStateRepository
import com.pulseguard.engine.HealthChecker
import com.pulseguard.engine.PulseEngine
import com.pulseguard.engine.PulseNotifications
import com.pulseguard.shizuku.ShizukuManager

/**
 * Process-wide service locator. Deliberately dependency-injection-framework-free: a handful
 * of singletons created here and reached via [from]. This process is re-created by the alarm
 * / boot receiver after an OEM kill, so keep [onCreate] cheap and side-effect-light.
 */
class PulseGuardApp : Application() {

    lateinit var shizukuManager: ShizukuManager
        private set
    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var appRepository: AppRepository
        private set
    lateinit var engineStateRepository: EngineStateRepository
        private set
    lateinit var alarmScheduler: AlarmScheduler
        private set
    lateinit var healthChecker: HealthChecker
        private set

    val pulseEngine: PulseEngine by lazy {
        PulseEngine(this, settingsRepository, shizukuManager, engineStateRepository)
    }

    override fun onCreate() {
        super.onCreate()
        PulseNotifications.ensureChannels(this)
        settingsRepository = SettingsRepository(this)
        appRepository = AppRepository(this)
        engineStateRepository = EngineStateRepository(this)
        alarmScheduler = AlarmScheduler(this)
        shizukuManager = ShizukuManager(this).also { it.initialize() }
        healthChecker = HealthChecker(this, shizukuManager)
    }

    companion object {
        fun from(context: Context): PulseGuardApp =
            context.applicationContext as PulseGuardApp
    }
}
