package com.pulseguard.engine

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.engineStateStore: DataStore<Preferences> by preferencesDataStore(name = "engine_state")

/**
 * Persists engine runtime telemetry so the UI shows real history even after the process was
 * killed and revived by the alarm. Written from the tick (receiver) and scheduler; observed
 * by the dashboard.
 */
class EngineStateRepository(context: Context) {

    private val store = context.applicationContext.engineStateStore

    val state: Flow<EngineState> = store.data.map { p ->
        EngineState(
            serviceRunning = p[Keys.SERVICE_RUNNING] ?: false,
            lastTickTime = p[Keys.LAST_TICK_TIME] ?: 0L,
            lastTickSkipped = p[Keys.LAST_TICK_SKIPPED] ?: false,
            lastTickReason = p[Keys.LAST_TICK_REASON] ?: "",
            lastPulsedCount = p[Keys.LAST_PULSED] ?: 0,
            lastFailedCount = p[Keys.LAST_FAILED] ?: 0,
            nextTickTime = p[Keys.NEXT_TICK_TIME] ?: 0L,
            totalTicks = p[Keys.TOTAL_TICKS] ?: 0,
            lastError = p[Keys.LAST_ERROR] ?: "",
        )
    }

    suspend fun recordTick(outcome: TickOutcome) {
        store.edit { p ->
            p[Keys.LAST_TICK_TIME] = outcome.timestamp
            p[Keys.LAST_TICK_SKIPPED] = outcome.skipped
            p[Keys.LAST_TICK_REASON] = outcome.skipReason ?: ""
            p[Keys.LAST_PULSED] = outcome.pulsedPackages.size
            p[Keys.LAST_FAILED] = outcome.failedPackages.size
            p[Keys.TOTAL_TICKS] = (p[Keys.TOTAL_TICKS] ?: 0) + 1
            p[Keys.LAST_ERROR] = if (outcome.failedPackages.isNotEmpty()) {
                "Failed: ${outcome.failedPackages.joinToString()}"
            } else {
                ""
            }
        }
    }

    suspend fun setNextTickTime(timeMs: Long) {
        store.edit { it[Keys.NEXT_TICK_TIME] = timeMs }
    }

    suspend fun setServiceRunning(running: Boolean) {
        store.edit { it[Keys.SERVICE_RUNNING] = running }
    }

    private object Keys {
        val SERVICE_RUNNING = booleanPreferencesKey("service_running")
        val LAST_TICK_TIME = longPreferencesKey("last_tick_time")
        val LAST_TICK_SKIPPED = booleanPreferencesKey("last_tick_skipped")
        val LAST_TICK_REASON = stringPreferencesKey("last_tick_reason")
        val LAST_PULSED = intPreferencesKey("last_pulsed")
        val LAST_FAILED = intPreferencesKey("last_failed")
        val NEXT_TICK_TIME = longPreferencesKey("next_tick_time")
        val TOTAL_TICKS = intPreferencesKey("total_ticks")
        val LAST_ERROR = stringPreferencesKey("last_error")
    }
}
