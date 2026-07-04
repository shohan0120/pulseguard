package com.pulseguard.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.notifLogStore: DataStore<Preferences> by preferencesDataStore(name = "notif_log")

/**
 * Records ONLY "package X last posted a notification at time T" — never any notification content,
 * title, or text. This is deliberately privacy-preserving: it exists purely so the dashboard can
 * prove notifications are being delivered to the user's protected apps.
 */
class NotificationLogRepository(context: Context) {

    private val store = context.applicationContext.notifLogStore

    /** Map of package name -> last-seen epoch millis. */
    val lastSeen: Flow<Map<String, Long>> = store.data.map { prefs ->
        prefs.asMap().entries
            .mapNotNull { (key, value) ->
                if (key.name.startsWith(PREFIX) && value is Long) {
                    key.name.removePrefix(PREFIX) to value
                } else {
                    null
                }
            }
            .toMap()
    }

    suspend fun record(packageName: String, epochMillis: Long) {
        store.edit { it[longPreferencesKey(PREFIX + packageName)] = epochMillis }
    }

    suspend fun lastSeenFor(packageName: String): Long? =
        store.data.map { it[longPreferencesKey(PREFIX + packageName)] }.first()

    private companion object {
        const val PREFIX = "seen_"
    }
}
