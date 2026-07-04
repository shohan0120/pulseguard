package com.pulseguard.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pulseguard.ui.apps.AppPickerScreen
import com.pulseguard.ui.battery.BatteryScreen
import com.pulseguard.ui.health.HealthScreen
import com.pulseguard.ui.home.HomeScreen
import com.pulseguard.ui.latency.LatencyScreen
import com.pulseguard.ui.onboarding.ShizukuWizardScreen
import com.pulseguard.ui.settings.SettingsScreen

/**
 * The main app shell (shown after onboarding). Start destination is fixed to Home so the
 * bottom-nav back-stack behaves predictably; onboarding is gated ahead of this by MainActivity.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PulseGuardRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val showBottomBar = currentRoute in BOTTOM_BAR_ROUTES
    val onWizard = currentRoute == Routes.SHIZUKU_WIZARD
    val onLatency = currentRoute == Routes.LATENCY
    val showBackBar = onWizard || onLatency

    Scaffold(
        topBar = {
            if (showBackBar) {
                CenterAlignedTopAppBar(
                    title = { Text(if (onLatency) "Latency test" else "Shizuku setup") },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (showBottomBar) {
                PulseBottomBar(navController = navController, currentRoute = currentRoute)
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onOpenWizard = { navController.navigate(Routes.SHIZUKU_WIZARD) },
                    onOpenHealth = { navController.navigateTab(Routes.HEALTH) },
                    onOpenBattery = { navController.navigateTab(Routes.BATTERY) },
                    onOpenLatency = { navController.navigate(Routes.LATENCY) },
                    onOpenApps = { navController.navigateTab(Routes.APPS) },
                )
            }
            composable(Routes.APPS) { AppPickerScreen() }
            composable(Routes.HEALTH) {
                HealthScreen(onOpenApps = { navController.navigateTab(Routes.APPS) })
            }
            composable(Routes.BATTERY) { BatteryScreen() }
            composable(Routes.LATENCY) { LatencyScreen() }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onOpenApps = { navController.navigateTab(Routes.APPS) },
                    onOpenWizard = { navController.navigate(Routes.SHIZUKU_WIZARD) },
                )
            }
            composable(Routes.SHIZUKU_WIZARD) {
                ShizukuWizardScreen(
                    isOnboarding = false,
                    onFinished = { navController.popBackStack() },
                )
            }
        }
    }
}

/** Navigate to a top-level tab with the standard single-top / restore-state behaviour. */
private fun NavHostController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun PulseBottomBar(navController: NavHostController, currentRoute: String?) {
    NavigationBar {
        TopDestination.entries.forEach { destination ->
            NavigationBarItem(
                selected = currentRoute == destination.route,
                onClick = { navController.navigateTab(destination.route) },
                icon = { Icon(destination.icon, contentDescription = destination.label) },
                label = { Text(destination.label) },
            )
        }
    }
}
