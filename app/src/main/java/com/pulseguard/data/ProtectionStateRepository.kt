package com.pulseguard.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.protectionStore: DataStore<Preferences> by preferencesDataStore(name = "protection_state")

/**
 * Remembers whether each protected app's Shizuku-readable protections were last seen healthy, so
 * the watchdog can detect a green → red regression (e.g. a setting silently lapsed after a reboot
 * or app update) and reapply it.
 */
class ProtectionStateRepository(context: Context) {

    private val store = context.applicationContext.protectionStore

    /** null = never recorded yet. */
    suspend fun wasHealthy(packageName: String): Boolean? =
        store.data.map { it[booleanPreferencesKey(PREFIX + packageName)] }.first()

    suspend fun setHealthy(packageName: String, healthy: Boolean) {
        store.edit { it[booleanPreferencesKey(PREFIX + packageName)] = healthy }
    }

    private companion object {
        const val PREFIX = "healthy_"
    }
}
