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
 * Fired by the self-rescheduling exact alarm. Kept intentionally thin: it hands the tick to
 * the foreground service (which has no broadcast time limit) and re-arms the next alarm.
 *
 * Starting the FGS here is permitted because this broadcast originates from an exact alarm
 * (`setExactAndAllowWhileIdle`), which grants a short FGS-start exemption.
 */
class PulseAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val app = PulseGuardApp.from(context)
        val pending = goAsync()
        scope.launch {
            try {
                val settings = app.settingsRepository.snapshot()
                if (!settings.engineEnabled) {
                    app.alarmScheduler.cancel()
                    return@launch
                }
                // Re-arm the NEXT alarm FIRST so the self-rescheduling chain survives even if the
                // service start below throws (e.g. an FGS-start exemption isn't honored this tick).
                val nextTime = app.alarmScheduler.scheduleNext(settings)
                app.engineStateRepository.setNextTickTime(nextTime)
                // Then hand the actual tick to the FGS. A start failure must not kill the chain.
                try {
                    KeepAliveService.tick(context)
                } catch (t: Throwable) {
                    Log.w(TAG, "Could not start tick service; will retry next alarm", t)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Alarm handling failed", t)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "PulseAlarmReceiver"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
