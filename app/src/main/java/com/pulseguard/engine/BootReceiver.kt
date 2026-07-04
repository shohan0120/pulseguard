package com.pulseguard.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.pulseguard.PulseGuardApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Re-arms the engine after a reboot or app update. Note: after reboot Shizuku itself is not
 * yet active (it needs re-activation unless the device is rooted). We still start the service;
 * the first tick will detect Shizuku-down and surface the alert so the user knows to reactivate.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> Unit
            else -> return
        }

        val app = PulseGuardApp.from(context)
        val pending = goAsync()
        scope.launch {
            try {
                val settings = app.settingsRepository.snapshot()
                if (!settings.engineEnabled) return@launch
                PulseNotifications.ensureChannels(context)
                // Only re-arm the alarm here. Starting the FGS is left to the alarm-triggered
                // path (KeepAliveService.tick from PulseAlarmReceiver), which is the compliant way
                // to launch a specialUse FGS from the background on Android 14+.
                val nextTime = app.alarmScheduler.scheduleAfterBoot()
                app.engineStateRepository.setNextTickTime(nextTime)
            } catch (t: Throwable) {
                Log.e(TAG, "Boot re-arm failed", t)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "BootReceiver"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
