package com.pulseguard.engine

import android.content.Context
import com.pulseguard.data.PulseSettings
import com.pulseguard.shizuku.ShizukuManager

/** Modeled worst-case battery cost of the engine, plus any raw stats we can read. */
data class BatteryEstimate(
    val ticksPerDay: Int,
    val estimatedMahPerDay: Double,
    val estimatedDailyPercent: Double,
    val perAppCount: Int,
    val effectiveDayIntervalMin: Int,
    val effectiveNightIntervalMin: Int,
)

/** Raw battery-stats excerpt read via Shizuku, if available. */
data class BatteryStatsReading(
    val available: Boolean,
    val excerpt: String,
)

/**
 * Estimates PulseGuard's own battery cost. The live estimate is a transparent model
 * (ticks/day × per-tick cost); the raw reading is a best-effort parse of `dumpsys batterystats`
 * via Shizuku, shown as supporting detail because a clean per-app mAh figure isn't reliably
 * exposed across ROMs.
 */
class BatteryInspector(
    private val context: Context,
    private val shizuku: ShizukuManager,
) {

    fun estimate(settings: PulseSettings): BatteryEstimate {
        val dayInterval = settings.intervalMinutes
        val nightInterval = if (settings.nightBackoffEnabled) dayInterval * 2 else dayInterval

        val nightHours = if (settings.nightBackoffEnabled) nightWindowHours(settings) else 0
        val dayHours = 24 - nightHours

        val dayTicks = if (dayInterval > 0) dayHours * 60 / dayInterval else 0
        val nightTicks = if (nightInterval > 0) nightHours * 60 / nightInterval else 0
        val ticksPerDay = dayTicks + nightTicks

        val appCount = settings.selectedPackages.size.coerceAtLeast(1)
        val mahPerDay = ticksPerDay * (BASE_MAH_PER_TICK + PER_APP_MAH * appCount)
        val dailyPercent = mahPerDay / TYPICAL_BATTERY_MAH * 100.0

        return BatteryEstimate(
            ticksPerDay = ticksPerDay,
            estimatedMahPerDay = mahPerDay,
            estimatedDailyPercent = dailyPercent,
            perAppCount = settings.selectedPackages.size,
            effectiveDayIntervalMin = dayInterval,
            effectiveNightIntervalMin = nightInterval,
        )
    }

    suspend fun readRawStats(): BatteryStatsReading {
        if (!shizuku.isReady()) {
            return BatteryStatsReading(available = false, excerpt = "Shizuku not connected.")
        }
        val pkg = context.packageName
        // Narrow the (large) dump to lines mentioning our package.
        val result = shizuku.exec("dumpsys batterystats $pkg | grep -iE '$pkg|Computed drain|Estimated' | head -n 40")
        val text = result.output.trim()
        return if (result.isSuccess && text.isNotEmpty()) {
            BatteryStatsReading(available = true, excerpt = text.take(2000))
        } else {
            BatteryStatsReading(
                available = false,
                excerpt = "No per-app battery data yet — Android needs some runtime before it reports drain.",
            )
        }
    }

    private fun nightWindowHours(settings: PulseSettings): Int {
        val start = settings.nightStartHour
        val end = settings.nightEndHour
        if (start == end) return 0
        return if (start < end) end - start else (24 - start) + end
    }

    private companion object {
        const val BASE_MAH_PER_TICK = 0.15
        const val PER_APP_MAH = 0.05
        const val TYPICAL_BATTERY_MAH = 4500.0
    }
}
