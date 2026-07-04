package com.pulseguard.engine

import android.content.Context
import com.pulseguard.data.AppRepository
import com.pulseguard.shizuku.ShizukuManager

/** Traffic-light state for a single health check. */
enum class CheckState { OK, WARN, FAIL, UNKNOWN }

/** Where a "Fix" button should deep-link the user. Resolved to an Intent by DeepLinks. */
enum class FixTarget {
    BATTERY_OPTIMIZATION,
    APP_DETAILS,
    NOTIFICATION_SETTINGS,
    AUTOSTART,
}

data class HealthCheck(
    val id: String,
    val label: String,
    val state: CheckState,
    val detail: String,
    val fixTarget: FixTarget? = null,
)

data class AppHealth(
    val packageName: String,
    val label: String,
    val checks: List<HealthCheck>,
    val shizukuBacked: Boolean,
) {
    val overall: CheckState
        get() = when {
            checks.any { it.state == CheckState.FAIL } -> CheckState.FAIL
            checks.any { it.state == CheckState.WARN } -> CheckState.WARN
            checks.any { it.state == CheckState.UNKNOWN } -> CheckState.UNKNOWN
            else -> CheckState.OK
        }
}

/**
 * Runs the read-only, per-app diagnostics via Shizuku shell. Everything degrades to an
 * UNKNOWN "guided-only" check when Shizuku is unavailable, and every non-OK check offers a
 * deep-link so the user can fix it manually.
 *
 * Honesty note: MIUI/HyperOS "Autostart" is intentionally NOT probed here — it isn't reliably
 * readable via any API — so it is surfaced as a guided step on the dashboard instead.
 */
class HealthChecker(
    context: Context,
    private val shizuku: ShizukuManager,
) {
    private val appRepository = AppRepository(context)

    suspend fun check(packageName: String, label: String? = null): AppHealth {
        val name = label ?: appRepository.labelFor(packageName)
        if (!shizuku.isReady()) return guidedOnly(packageName, name)

        return AppHealth(
            packageName = packageName,
            label = name,
            checks = listOf(
                checkBatteryOptimization(packageName),
                checkBackgroundExecution(packageName),
                checkNotifications(packageName),
            ),
            shizukuBacked = true,
        )
    }

    private suspend fun checkBatteryOptimization(pkg: String): HealthCheck {
        val result = shizuku.exec("dumpsys deviceidle whitelist")
        // Lines look like "system,com.foo,10012" / "user,com.bar,10234"; match an exact
        // comma-delimited field to avoid substring false-positives (e.g. com.foo vs org.com.foo).
        val exempt = result.isSuccess &&
            result.output.lineSequence().any { line ->
                line.split(',').any { it.trim() == pkg }
            }
        return HealthCheck(
            id = "battery_opt",
            label = "Battery optimization",
            state = if (exempt) CheckState.OK else CheckState.WARN,
            detail = if (exempt) {
                "Exempt from Doze — allowed to run in the background."
            } else {
                "Not exempt. PulseGuard still pulses it, but a manual exemption is more reliable."
            },
            fixTarget = if (exempt) null else FixTarget.BATTERY_OPTIMIZATION,
        )
    }

    private suspend fun checkBackgroundExecution(pkg: String): HealthCheck {
        val result = shizuku.exec("cmd appops get $pkg RUN_ANY_IN_BACKGROUND")
        val line = result.firstLine.lowercase()
        val state = when {
            !result.isSuccess -> CheckState.UNKNOWN
            line.contains("ignore") || line.contains("deny") -> CheckState.FAIL
            line.contains("allow") || line.contains("default") || line.contains("no operations") ->
                CheckState.OK
            else -> CheckState.UNKNOWN
        }
        return HealthCheck(
            id = "background",
            label = "Background execution",
            state = state,
            detail = when (state) {
                CheckState.FAIL -> "Background is restricted — notifications will be delayed or dropped."
                CheckState.OK -> "Background execution is allowed."
                else -> "Couldn't read the background state."
            },
            fixTarget = if (state == CheckState.FAIL) FixTarget.APP_DETAILS else null,
        )
    }

    private suspend fun checkNotifications(pkg: String): HealthCheck {
        val result = shizuku.exec("dumpsys package $pkg")
        val granted = result.output.contains("android.permission.POST_NOTIFICATIONS: granted=true")
        val denied = result.output.contains("android.permission.POST_NOTIFICATIONS: granted=false")
        val state = when {
            granted -> CheckState.OK
            denied -> CheckState.FAIL
            else -> CheckState.UNKNOWN
        }
        return HealthCheck(
            id = "notifications",
            label = "Notifications",
            state = state,
            detail = when (state) {
                CheckState.OK -> "Notifications are permitted."
                CheckState.FAIL -> "Notifications are turned off for this app."
                else -> "Notification permission state is unknown (pre-Android 13 or not requested)."
            },
            fixTarget = if (state == CheckState.OK) null else FixTarget.NOTIFICATION_SETTINGS,
        )
    }

    private fun guidedOnly(pkg: String, label: String): AppHealth = AppHealth(
        packageName = pkg,
        label = label,
        shizukuBacked = false,
        checks = listOf(
            HealthCheck(
                "battery_opt", "Battery optimization", CheckState.UNKNOWN,
                "Connect Shizuku to auto-check, or exempt it yourself.", FixTarget.BATTERY_OPTIMIZATION,
            ),
            HealthCheck(
                "background", "Background execution", CheckState.UNKNOWN,
                "Connect Shizuku to auto-check, or set it to Unrestricted yourself.", FixTarget.APP_DETAILS,
            ),
            HealthCheck(
                "notifications", "Notifications", CheckState.UNKNOWN,
                "Connect Shizuku to auto-check, or verify notifications are on.", FixTarget.NOTIFICATION_SETTINGS,
            ),
        ),
    )
}
