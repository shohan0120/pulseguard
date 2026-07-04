package com.pulseguard.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.PowerManager
import java.util.Calendar

/** Reads live device conditions used by the battery-saver skip logic and night backoff. */
class DeviceStateProbe(context: Context) {

    private val appContext = context.applicationContext
    private val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val batteryManager =
        appContext.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun isScreenOn(): Boolean = powerManager.isInteractive

    fun isCharging(): Boolean = batteryManager.isCharging

    fun isDeviceIdle(): Boolean = powerManager.isDeviceIdleMode

    /** Connected to Wi-Fi that the system does not consider metered. */
    fun isUnmeteredWifi(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    /** Battery level as a 0..100 percentage, or -1 if unavailable. */
    fun batteryLevelPercent(): Int =
        batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

    /**
     * True if [hour] falls inside the night window. Handles windows that wrap past midnight
     * (e.g. 23 → 7).
     */
    fun isNight(startHour: Int, endHour: Int, hour: Int = currentHour()): Boolean {
        if (startHour == endHour) return false
        return if (startHour < endHour) {
            hour in startHour until endHour
        } else {
            hour >= startHour || hour < endHour
        }
    }

    fun currentHour(): Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
}
