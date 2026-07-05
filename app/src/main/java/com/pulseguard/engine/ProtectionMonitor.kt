package com.pulseguard.engine

import android.content.Context
import com.pulseguard.data.ProtectionStateRepository
import com.pulseguard.data.SettingsRepository
import com.pulseguard.shizuku.ShizukuManager

/**
 * The real value: keeps the user's Shizuku-readable protections in place. On each run it re-checks
 * every protected app; if one regressed from healthy to failing (a setting silently lapsed after a
 * reboot or app update), it reapplies the Shizuku-fixable layers and notifies the user. Manual
 * layers (MIUI Autostart etc.) can't be read, so they aren't touched here.
 */
class ProtectionMonitor(
    private val context: Context,
    private val healthChecker: HealthChecker,
    private val settings: SettingsRepository,
    private val protectionState: ProtectionStateRepository,
    private val shizuku: ShizukuManager,
) {

    suspend fun reverifyAndMaintain() {
        if (!shizuku.isReady()) return
        val packages = settings.snapshot().selectedPackages
        val reapplied = mutableListOf<String>()

        for (pkg in packages) {
            var health = healthChecker.check(pkg)
            val wasHealthy = protectionState.wasHealthy(pkg)

            if (wasHealthy == true && !health.readableHealthy) {
                // Regression — reapply every fixable readable layer, then re-check.
                health.checks
                    .filter { it.autoFixable && it.state != CheckState.OK }
                    .forEach { healthChecker.autoFix(pkg, it.id) }
                health = healthChecker.check(pkg)
                reapplied += health.label
            }

            protectionState.setHealthy(pkg, health.readableHealthy)
        }

        if (reapplied.isNotEmpty()) {
            PulseNotifications.notifyProtectionLapsed(context, reapplied)
        }
    }
}
