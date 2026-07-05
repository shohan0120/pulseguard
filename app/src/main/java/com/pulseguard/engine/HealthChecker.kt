package com.pulseguard.engine

import android.content.Context
import android.os.Build
import com.pulseguard.data.AppRepository
import com.pulseguard.shizuku.ShizukuManager

/** Traffic-light state for a single protection layer. MANUAL = can't be read, user must verify. */
enum class CheckState { OK, WARN, FAIL, UNKNOWN, MANUAL }

/** Where a "Fix"/"Verify" button should deep-link the user. Resolved to an Intent by DeepLinks. */
enum class FixTarget {
    BATTERY_OPTIMIZATION,
    APP_DETAILS,
    NOTIFICATION_SETTINGS,
    AUTOSTART,
    OTHER_PERMISSIONS,
}

data class HealthCheck(
    val id: String,
    val label: String,
    val state: CheckState,
    val detail: String,
    val fixTarget: FixTarget? = null,
    /** True when Shizuku can apply this fix directly (see [HealthChecker.autoFix]). */
    val autoFixable: Boolean = false,
) {
    /** A layer we can't read and can only ask the user to verify manually (e.g. MIUI Autostart). */
    val isManual: Boolean get() = state == CheckState.MANUAL
}

data class AppHealth(
    val packageName: String,
    val label: String,
    val checks: List<HealthCheck>,
    val shizukuBacked: Boolean,
) {
    private val readable get() = checks.filter { !it.isManual }

    /** Overall status ignores manual (unreadable) layers — they can't make an app "red". */
    val overall: CheckState
        get() = when {
            readable.any { it.state == CheckState.FAIL } -> CheckState.FAIL
            readable.any { it.state == CheckState.WARN } -> CheckState.WARN
            readable.any { it.state == CheckState.UNKNOWN } -> CheckState.UNKNOWN
            readable.isEmpty() -> CheckState.UNKNOWN
            else -> CheckState.OK
        }

    /** No readable protection is failing. Used by the watchdog to detect regressions. */
    val readableHealthy: Boolean get() = readable.none { it.state == CheckState.FAIL }
}

/**
 * Reads the per-app protection layers PulseGuard can (via Shizuku shell) and lists the ones it
 * cannot as manual "verify" steps. Everything degrades to a guided-only view without Shizuku.
 *
 * Honesty: MIUI/HyperOS Autostart, background pop-up, and per-app battery keep-alive are NOT
 * readable via any API, so they are presented as manual verify steps — never as a detected state.
 */
class HealthChecker(
    context: Context,
    private val shizuku: ShizukuManager,
) {
    private val appRepository = AppRepository(context)

    private val isMiui: Boolean by lazy {
        val brand = Build.BRAND.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()
        "xiaomi" in manufacturer || "xiaomi" in brand || "redmi" in brand || "poco" in brand
    }

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
            ) + manualLayers(),
            shizukuBacked = true,
        )
    }

    private suspend fun checkBatteryOptimization(pkg: String): HealthCheck {
        val result = shizuku.exec("dumpsys deviceidle whitelist")
        val exempt = result.isSuccess &&
            result.output.lineSequence().any { line -> line.split(',').any { it.trim() == pkg } }
        return HealthCheck(
            id = "battery_opt",
            label = "Battery optimization",
            state = if (exempt) CheckState.OK else CheckState.WARN,
            detail = if (exempt) {
                "Exempt from Doze — allowed to run in the background."
            } else {
                "Not exempt from Doze. Add it so the system stops deferring its background work."
            },
            fixTarget = if (exempt) null else FixTarget.BATTERY_OPTIMIZATION,
            autoFixable = !exempt,
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
                CheckState.FAIL -> "Background is restricted — its push connection will be killed."
                CheckState.OK -> "Background execution is allowed."
                else -> "Couldn't read the background state."
            },
            fixTarget = if (state == CheckState.FAIL) FixTarget.APP_DETAILS else null,
            autoFixable = state != CheckState.OK,
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
            autoFixable = state != CheckState.OK,
        )
    }

    /** Layers that no app can read — presented as manual verify steps, never as a detected state. */
    private fun manualLayers(): List<HealthCheck> = if (isMiui) {
        listOf(
            HealthCheck(
                "autostart", "Autostart", CheckState.MANUAL,
                "Can't be read on MIUI. Verify Autostart is ON — otherwise the app is killed regardless.",
                FixTarget.AUTOSTART,
            ),
            HealthCheck(
                "background_popup", "Background pop-up", CheckState.MANUAL,
                "In Other permissions, allow \"Display pop-up windows while running in the background\".",
                FixTarget.OTHER_PERMISSIONS,
            ),
            HealthCheck(
                "battery_saver", "Battery saver", CheckState.MANUAL,
                "Set this app's battery saver to \"No restrictions\".",
                FixTarget.APP_DETAILS,
            ),
        )
    } else {
        listOf(
            HealthCheck(
                "battery_unrestricted", "Battery restriction", CheckState.MANUAL,
                "Verify this app is set to Unrestricted in its battery settings.",
                FixTarget.APP_DETAILS,
            ),
        )
    }

    /**
     * Applies a fix directly via the Shizuku shell, then the caller re-checks. Only the shell-
     * fixable readable layers are handled; manual layers return false. The re-check confirms the
     * new state.
     */
    suspend fun autoFix(packageName: String, checkId: String): Boolean {
        if (!shizuku.isReady()) return false
        val command = when (checkId) {
            "battery_opt" -> "cmd deviceidle whitelist +$packageName"
            "background" ->
                "cmd appops set $packageName RUN_ANY_IN_BACKGROUND allow; " +
                    "cmd appops set $packageName RUN_IN_BACKGROUND allow"
            "notifications" ->
                "pm grant $packageName android.permission.POST_NOTIFICATIONS 2>/dev/null; " +
                    "cmd appops set $packageName POST_NOTIFICATION allow 2>/dev/null; true"
            else -> return false
        }
        return shizuku.exec(command).isSuccess
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
        ) + manualLayers(),
    )
}
