package com.pulseguard.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.pulseDataStore: DataStore<Preferences> by preferencesDataStore(name = "pulse_settings")

/**
 * Single source of truth for [PulseSettings], backed by Preferences DataStore.
 * Exposes a reactive [settings] flow plus a [snapshot] for one-shot reads inside the
 * engine tick (which runs without a UI scope).
 */
class SettingsRepository(context: Context) {

    private val dataStore = context.applicationContext.pulseDataStore

    val settings: Flow<PulseSettings> = dataStore.data.map(::toSettings)

    suspend fun snapshot(): PulseSettings = toSettings(dataStore.data.first())

    private fun toSettings(prefs: Preferences): PulseSettings = PulseSettings(
        engineEnabled = prefs[Keys.ENGINE_ENABLED] ?: false,
        intervalMinutes = prefs[Keys.INTERVAL_MINUTES] ?: PulseSettings.DEFAULT_INTERVAL_MINUTES,
        selectedPackages = prefs[Keys.SELECTED_PACKAGES] ?: emptySet(),
        skipWhenScreenOn = prefs[Keys.SKIP_SCREEN_ON] ?: true,
        skipWhenCharging = prefs[Keys.SKIP_CHARGING] ?: true,
        skipWhenIdleOnWifi = prefs[Keys.SKIP_IDLE_WIFI] ?: false,
        nightBackoffEnabled = prefs[Keys.NIGHT_BACKOFF] ?: true,
        nightStartHour = prefs[Keys.NIGHT_START] ?: PulseSettings.DEFAULT_NIGHT_START,
        nightEndHour = prefs[Keys.NIGHT_END] ?: PulseSettings.DEFAULT_NIGHT_END,
        tempWhitelistSeconds = prefs[Keys.WHITELIST_SECONDS] ?: PulseSettings.DEFAULT_WHITELIST_SECONDS,
        onboardingCompleted = prefs[Keys.ONBOARDING_DONE] ?: false,
    )

    suspend fun setEngineEnabled(enabled: Boolean) = edit { it[Keys.ENGINE_ENABLED] = enabled }

    suspend fun setIntervalMinutes(minutes: Int) = edit { it[Keys.INTERVAL_MINUTES] = minutes }

    suspend fun setSelectedPackages(packages: Set<String>) =
        edit { it[Keys.SELECTED_PACKAGES] = packages }

    suspend fun togglePackage(pkg: String, selected: Boolean) = edit { prefs ->
        val current = prefs[Keys.SELECTED_PACKAGES] ?: emptySet()
        prefs[Keys.SELECTED_PACKAGES] = if (selected) current + pkg else current - pkg
    }

    suspend fun setSkipWhenScreenOn(value: Boolean) = edit { it[Keys.SKIP_SCREEN_ON] = value }

    suspend fun setSkipWhenCharging(value: Boolean) = edit { it[Keys.SKIP_CHARGING] = value }

    suspend fun setSkipWhenIdleOnWifi(value: Boolean) = edit { it[Keys.SKIP_IDLE_WIFI] = value }

    suspend fun setNightBackoffEnabled(value: Boolean) = edit { it[Keys.NIGHT_BACKOFF] = value }

    suspend fun setNightWindow(startHour: Int, endHour: Int) = edit {
        it[Keys.NIGHT_START] = startHour
        it[Keys.NIGHT_END] = endHour
    }

    suspend fun setTempWhitelistSeconds(seconds: Int) = edit { it[Keys.WHITELIST_SECONDS] = seconds }

    suspend fun setOnboardingCompleted(value: Boolean) = edit { it[Keys.ONBOARDING_DONE] = value }

    private suspend inline fun edit(crossinline block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        dataStore.edit { block(it) }
    }

    private object Keys {
        val ENGINE_ENABLED = booleanPreferencesKey("engine_enabled")
        val INTERVAL_MINUTES = intPreferencesKey("interval_minutes")
        val SELECTED_PACKAGES = stringSetPreferencesKey("selected_packages")
        val SKIP_SCREEN_ON = booleanPreferencesKey("skip_screen_on")
        val SKIP_CHARGING = booleanPreferencesKey("skip_charging")
        val SKIP_IDLE_WIFI = booleanPreferencesKey("skip_idle_wifi")
        val NIGHT_BACKOFF = booleanPreferencesKey("night_backoff")
        val NIGHT_START = intPreferencesKey("night_start")
        val NIGHT_END = intPreferencesKey("night_end")
        val WHITELIST_SECONDS = intPreferencesKey("whitelist_seconds")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    }
}
