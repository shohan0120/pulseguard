package com.pulseguard.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.ui.graphics.vector.ImageVector

/** Route constants for the whole app. */
object Routes {
    const val WELCOME = "welcome"
    const val SHIZUKU_WIZARD = "shizuku_wizard"
    const val HOME = "home"
    const val APPS = "apps"
    const val BATTERY = "battery"
    const val LATENCY = "latency"
    const val SETTINGS = "settings"
    const val LIMITATIONS = "limitations"
}

/** The bottom-navigation tabs. The Protection dashboard is home. */
enum class TopDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    HOME(Routes.HOME, "Protection", Icons.Outlined.Shield),
    APPS(Routes.APPS, "Apps", Icons.Outlined.Apps),
    SETTINGS(Routes.SETTINGS, "Settings", Icons.Outlined.Settings),
}

/** Routes that show the bottom bar. */
val BOTTOM_BAR_ROUTES = TopDestination.entries.map { it.route }.toSet()
