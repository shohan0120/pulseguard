package com.pulseguard.util

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import com.pulseguard.engine.FixTarget
import com.pulseguard.engine.PulseNotificationListener

/**
 * Best-effort deep links into system / OEM settings. Everything is wrapped so an unresolved
 * intent (missing OEM activity, locked-down ROM) degrades to a sensible fallback rather than
 * crashing — which is essential given how varied MIUI/HyperOS builds are.
 */
object DeepLinks {

    private const val TAG = "DeepLinks"

    fun openFixTarget(context: Context, target: FixTarget, packageName: String) {
        when (target) {
            FixTarget.BATTERY_OPTIMIZATION -> openBatteryOptimizationList(context)
            FixTarget.APP_DETAILS -> openAppDetails(context, packageName)
            FixTarget.NOTIFICATION_SETTINGS -> openAppNotificationSettings(context, packageName)
            FixTarget.AUTOSTART -> openAutostart(context, packageName)
        }
    }

    fun openAppDetails(context: Context, packageName: String) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null),
        )
        start(context, intent) { openSettings(context) }
    }

    fun openAppNotificationSettings(context: Context, packageName: String) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        start(context, intent) { openAppDetails(context, packageName) }
    }

    /** True if the user has granted PulseGuard's notification listener access. */
    fun isNotificationAccessGranted(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    /**
     * Opens the Notification Access screen. On API 30+ we deep-link straight to PulseGuard's own
     * listener toggle; otherwise we fall back to the full listeners list.
     */
    fun openNotificationAccessSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val component = ComponentName(context, PulseNotificationListener::class.java)
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString())
            start(context, intent) { openNotificationAccessList(context) }
        } else {
            openNotificationAccessList(context)
        }
    }

    private fun openNotificationAccessList(context: Context) {
        start(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) { openSettings(context) }
    }

    fun openBatteryOptimizationList(context: Context) {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        start(context, intent) { openSettings(context) }
    }

    /** Prompt the system to exempt PulseGuard itself from battery optimization. */
    fun requestIgnoreBatteryOptimizationsForSelf(context: Context) {
        val intent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.fromParts("package", context.packageName, null),
        )
        start(context, intent) { openBatteryOptimizationList(context) }
    }

    /** Opens the exact-alarm permission screen (Android 12+). */
    fun requestExactAlarmPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                .setData(Uri.fromParts("package", context.packageName, null))
            start(context, intent) { openAppDetails(context, context.packageName) }
        } else {
            openAppDetails(context, context.packageName)
        }
    }

    /**
     * MIUI/HyperOS Autostart. This state cannot be read via public API, so we can only guide
     * the user here. Tries the known OEM activities, then falls back to app details.
     */
    fun openAutostart(context: Context, packageName: String) {
        val candidates = listOf(
            ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity",
            ),
            ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartDetailManagementActivity",
            ),
            ComponentName(
                "com.letv.android.letvsafe",
                "com.letv.android.letvsafe.AutobootManageActivity",
            ),
            ComponentName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity",
            ),
            ComponentName(
                "com.oppo.safe",
                "com.oppo.safe.permission.startup.StartupAppListActivity",
            ),
            ComponentName(
                "com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
            ),
        )
        for (component in candidates) {
            val intent = Intent().setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (tryStart(context, intent)) return
        }
        // No OEM autostart manager resolved — send them to app details as a fallback.
        openAppDetails(context, packageName)
    }

    fun openSettings(context: Context) {
        start(context, Intent(Settings.ACTION_SETTINGS)) {
            toast(context, "Couldn't open Settings on this device.")
        }
    }

    /** Launches the installed Shizuku app, if present. Returns false if it isn't installed. */
    fun openShizuku(context: Context): Boolean {
        val packages = listOf("moe.shizuku.privileged.api")
        for (pkg in packages) {
            val launch = context.packageManager.getLaunchIntentForPackage(pkg)
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (tryStart(context, launch)) return true
            }
        }
        return false
    }

    /**
     * Shizuku is NOT on the Play Store, so we link the user to the GitHub releases page to
     * download and sideload the APK.
     */
    fun openShizukuGitHubReleases(context: Context) {
        openUrl(context, "https://github.com/RikkaApps/Shizuku/releases")
    }

    /** F-Droid source for Shizuku (it lives in the IzzyOnDroid repo, not the main F-Droid repo). */
    fun openShizukuFDroid(context: Context) {
        openUrl(context, "https://apt.izzysoft.de/fdroid/index/apk/moe.shizuku.privileged.api")
    }

    fun openShizukuGuide(context: Context) {
        openUrl(context, "https://shizuku.rikka.app/guide/setup/")
    }

    /** Wireless debugging developer setting (Android 11+), the modern way to start Shizuku. */
    fun openWirelessDebugging(context: Context) {
        // No stable public action for the exact page; land on Developer options.
        openDeveloperOptions(context)
    }

    fun openUrl(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        start(context, intent) { toast(context, "No browser available.") }
    }

    fun openDeveloperOptions(context: Context) {
        start(context, Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) {
            openSettings(context)
        }
    }

    private inline fun start(context: Context, intent: Intent, fallback: () -> Unit) {
        if (!tryStart(context, intent)) fallback()
    }

    private fun tryStart(context: Context, intent: Intent): Boolean {
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: SecurityException) {
            Log.w(TAG, "Blocked opening $intent", e)
            false
        }
    }

    private fun toast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}
