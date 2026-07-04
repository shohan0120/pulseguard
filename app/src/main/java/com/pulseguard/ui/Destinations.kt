package com.pulseguard.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/** Route constants for the whole app. */
object Routes {
    const val WELCOME = "welcome"
    const val SHIZUKU_WIZARD = "shizuku_wizard"
    const val HOME = "home"
    const val APPS = "apps"
    const val HEALTH = "health"
    const val BATTERY = "battery"
    const val LATENCY = "latency"
    const val SETTINGS = "settings"
}

/** The bottom-navigation tabs. */
enum class TopDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    HOME(Routes.HOME, "Home", Icons.Outlined.Home),
    APPS(Routes.APPS, "Apps", Icons.Outlined.Apps),
    HEALTH(Routes.HEALTH, "Health", Icons.Outlined.HealthAndSafety),
    BATTERY(Routes.BATTERY, "Battery", Icons.Outlined.BatteryChargingFull),
    SETTINGS(Routes.SETTINGS, "Settings", Icons.Outlined.Settings),
}

/** Routes that show the bottom bar. */
val BOTTOM_BAR_ROUTES = TopDestination.entries.map { it.route }.toSet()
