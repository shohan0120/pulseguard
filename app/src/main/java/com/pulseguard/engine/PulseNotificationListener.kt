package com.pulseguard.engine

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.pulseguard.PulseGuardApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Optional, additive "proof of delivery" tracker. Bound by the system only when the user grants
 * Notification Access. For the user's PROTECTED apps it records ONLY the package name and the
 * time a notification was posted — never the title, text, or any other content. The whole app
 * works fine without this; it just powers the "last notification: Xm ago" line on the dashboard.
 */
class PulseNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val app get() = PulseGuardApp.from(this)

    @Volatile
    private var protectedPackages: Set<String> = emptySet()
    private val lastWrite = ConcurrentHashMap<String, Long>()

    override fun onCreate() {
        super.onCreate()
        // Keep the protected-set cached in memory so onNotificationPosted stays cheap.
        scope.launch {
            app.settingsRepository.settings.collect { protectedPackages = it.selectedPackages }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val pkg = sbn?.packageName ?: return
        if (pkg !in protectedPackages) return

        val now = System.currentTimeMillis()
        val previous = lastWrite[pkg] ?: 0L
        if (now - previous < THROTTLE_MS) return // avoid hammering DataStore for chatty apps
        lastWrite[pkg] = now

        scope.launch { app.notificationLogRepository.record(pkg, now) }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val THROTTLE_MS = 8_000L
    }
}
