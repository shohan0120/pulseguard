package com.pulseguard.engine

import android.content.Context
import android.util.Log
import com.pulseguard.data.PulseSettings
import com.pulseguard.data.SettingsRepository
import com.pulseguard.shizuku.ShizukuManager

/**
 * The heart of PulseGuard. On each tick it evaluates the battery-saver conditions and, if it
 * should proceed, grants every selected app a short device-idle temp-whitelist window (so its
 * push socket can reconnect) plus best-effort un-hibernation / active standby bucket.
 */
class PulseEngine(
    context: Context,
    private val settingsRepository: SettingsRepository,
    private val shizuku: ShizukuManager,
    private val engineState: EngineStateRepository,
) {
    private val appContext = context.applicationContext
    private val probe = DeviceStateProbe(appContext)

    suspend fun runTick(): TickOutcome {
        val settings = settingsRepository.snapshot()
        val now = System.currentTimeMillis()

        // Keep Shizuku status current so the watchdog reacts to any change this tick surfaces.
        shizuku.refresh()

        val skip = evaluateSkip(settings)
        if (skip != null) {
            return finish(TickOutcome(now, skipped = true, skipReason = skip, shizukuReady = shizuku.isReady()))
        }

        if (settings.selectedPackages.isEmpty()) {
            return finish(TickOutcome(now, skipped = true, skipReason = "No apps selected"))
        }

        if (!shizuku.isReady()) {
            // The ShizukuWatchdog owns the "paused" notification; just skip this tick.
            return finish(
                TickOutcome(now, skipped = true, skipReason = "Shizuku not connected", shizukuReady = false)
            )
        }

        val pulsed = mutableListOf<String>()
        val failed = mutableListOf<String>()
        val durationMs = settings.tempWhitelistSeconds.coerceAtLeast(10) * 1000L

        for (pkg in settings.selectedPackages) {
            val result = shizuku.exec(buildPulseCommand(pkg, durationMs))
            if (result.isSuccess) {
                pulsed += pkg
            } else {
                failed += pkg
                Log.w(TAG, "Pulse failed for $pkg (exit ${result.exitCode}): ${result.output.take(200)}")
            }
        }

        return finish(
            TickOutcome(
                timestamp = now,
                skipped = false,
                pulsedPackages = pulsed,
                failedPackages = failed,
                shizukuReady = true,
            )
        )
    }

    private suspend fun finish(outcome: TickOutcome): TickOutcome {
        engineState.recordTick(outcome)
        return outcome
    }

    private fun evaluateSkip(settings: PulseSettings): String? {
        if (settings.skipWhenScreenOn && probe.isScreenOn()) return "Screen on"
        if (settings.skipWhenCharging && probe.isCharging()) return "Charging"
        if (settings.skipWhenIdleOnWifi && probe.isDeviceIdle() && probe.isUnmeteredWifi()) {
            return "Idle on Wi-Fi"
        }
        return null
    }

    /**
     * The privileged command run per app. The temp-whitelist exit code is what we report as
     * success; the auxiliary levers (standby bucket, un-hibernate) are best-effort and must not
     * mark the tick as failed if a given ROM lacks them.
     */
    private fun buildPulseCommand(pkg: String, durationMs: Long): String = buildString {
        append("cmd deviceidle tempwhitelist -d ").append(durationMs).append(' ').append(pkg).append("; ")
        append("RC=\$?; ")
        append("am set-standby-bucket ").append(pkg).append(" active >/dev/null 2>&1; ")
        append("cmd app_hibernation set-state --global ").append(pkg).append(" false >/dev/null 2>&1; ")
        append("cmd app_hibernation set-state ").append(pkg).append(" false >/dev/null 2>&1; ")
        append("exit \$RC")
    }

    private companion object {
        const val TAG = "PulseEngine"
    }
}
