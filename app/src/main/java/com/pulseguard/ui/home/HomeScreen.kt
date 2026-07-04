package com.pulseguard.ui.home

import android.app.Application
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.NetworkPing
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pulseguard.PulseGuardApp
import com.pulseguard.data.PulseSettings
import com.pulseguard.engine.EngineController
import com.pulseguard.engine.EngineState
import com.pulseguard.shizuku.ShizukuStatus
import com.pulseguard.ui.components.LabeledValue
import com.pulseguard.ui.components.PulseCard
import com.pulseguard.ui.components.SectionLabel
import com.pulseguard.ui.theme.failColor
import com.pulseguard.ui.theme.okColor
import com.pulseguard.util.DeepLinks
import com.pulseguard.util.TimeFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProtectedAppStatus(
    val packageName: String,
    val label: String,
    val lastNotification: Long?,
)

data class HomeUiState(
    val settings: PulseSettings,
    val engineState: EngineState,
    val shizukuStatus: ShizukuStatus,
    val protectedApps: List<ProtectedAppStatus>,
)

private data class HomeInputs(
    val settings: PulseSettings,
    val engineState: EngineState,
    val shizukuStatus: ShizukuStatus,
    val lastSeen: Map<String, Long>,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as PulseGuardApp

    val uiState: StateFlow<HomeUiState?> =
        combine(
            app.settingsRepository.settings,
            app.engineStateRepository.state,
            app.shizukuManager.status,
            app.notificationLogRepository.lastSeen,
        ) { settings, engine, shizuku, lastSeen ->
            HomeInputs(settings, engine, shizuku, lastSeen)
        }.mapLatest { input ->
            val apps = input.settings.selectedPackages
                .map { pkg -> ProtectedAppStatus(pkg, app.appRepository.labelFor(pkg), input.lastSeen[pkg]) }
                .sortedBy { it.label.lowercase() }
            HomeUiState(input.settings, input.engineState, input.shizukuStatus, apps)
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setEngineEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) EngineController.enable(app) else EngineController.disable(app)
        }
    }

    fun pulseNow() = EngineController.pulseNow(app)
    fun refreshShizuku() = app.shizukuManager.refresh()
    fun canScheduleExact(): Boolean = app.alarmScheduler.canScheduleExact()
    fun isNotificationAccessGranted(): Boolean = DeepLinks.isNotificationAccessGranted(app)
}

@Composable
fun HomeScreen(
    onOpenWizard: () -> Unit,
    onOpenHealth: () -> Unit,
    onOpenBattery: () -> Unit,
    onOpenLatency: () -> Unit,
    onOpenApps: () -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Re-check host-controlled states on resume (returning from Settings / Shizuku / access screen).
    var exactAlarmOk by remember { mutableStateOf(viewModel.canScheduleExact()) }
    var notifAccess by remember { mutableStateOf(viewModel.isNotificationAccessGranted()) }
    LifecycleResumeEffect(Unit) {
        viewModel.refreshShizuku()
        exactAlarmOk = viewModel.canScheduleExact()
        notifAccess = viewModel.isNotificationAccessGranted()
        onPauseOrDispose { }
    }

    val ui = state ?: return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        Text("PulseGuard", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Keeps your important apps reachable so notifications arrive on time.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        HeroCard(
            enabled = ui.settings.engineEnabled,
            shizukuReady = ui.shizukuStatus == ShizukuStatus.READY,
            selectedCount = ui.settings.selectedPackages.size,
            intervalMinutes = ui.settings.intervalMinutes,
            onToggle = viewModel::setEngineEnabled,
            onPulseNow = viewModel::pulseNow,
            onReactivate = onOpenWizard,
        )

        // Detailed guidance when Shizuku isn't ready (install / start / grant).
        if (ui.shizukuStatus != ShizukuStatus.READY) {
            ShizukuWarningCard(ui.shizukuStatus, onOpenWizard)
        }

        if (!exactAlarmOk) {
            ExactAlarmWarningCard(onOpenWizard)
        }

        if (ui.settings.selectedPackages.isEmpty()) {
            EmptySelectionCard(onOpenApps)
        } else {
            DeliverySection(
                apps = ui.protectedApps,
                notificationAccess = notifAccess,
                onEnableAccess = { DeepLinks.openNotificationAccessSettings(context) },
            )
        }

        StatusCard(ui.engineState, ui.settings)

        QuickActions(
            onOpenHealth = onOpenHealth,
            onOpenBattery = onOpenBattery,
            onOpenLatency = onOpenLatency,
        )
    }
}

@Composable
private fun HeroCard(
    enabled: Boolean,
    shizukuReady: Boolean,
    selectedCount: Int,
    intervalMinutes: Int,
    onToggle: (Boolean) -> Unit,
    onPulseNow: () -> Unit,
    onReactivate: () -> Unit,
) {
    val paused = enabled && !shizukuReady
    val protected = enabled && shizukuReady
    val targetAccent = when {
        protected -> okColor()
        paused -> failColor()
        else -> MaterialTheme.colorScheme.outline
    }
    val accent by animateColorAsState(targetAccent, label = "accent")

    PulseCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Shield, contentDescription = null, tint = accent, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        paused -> "Protection paused"
                        protected -> "Protected"
                        else -> "Protection is off"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (paused) accent else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    when {
                        paused -> "Shizuku needs reactivation"
                        protected -> "$selectedCount app${plural(selectedCount)} · every $intervalMinutes min"
                        else -> "Toggle on to start protecting apps"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
        when {
            paused -> {
                Spacer(Modifier.height(16.dp))
                Button(onClick = onReactivate, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Reactivate Shizuku")
                }
            }
            protected -> {
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = onPulseNow, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Pulse now")
                }
            }
        }
    }
}

@Composable
private fun DeliverySection(
    apps: List<ProtectedAppStatus>,
    notificationAccess: Boolean,
    onEnableAccess: () -> Unit,
) {
    PulseCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.NotificationsActive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            SectionLabel("Notification delivery")
        }
        Spacer(Modifier.height(10.dp))

        if (!notificationAccess) {
            Text(
                "See proof that notifications are actually arriving. Grant Notification access and " +
                    "PulseGuard will show when each protected app last received one (package + time only — " +
                    "never any content).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(onClick = onEnableAccess) { Text("Enable notification access") }
        } else {
            apps.forEach { appStatus ->
                LabeledValue(
                    label = appStatus.label,
                    value = appStatus.lastNotification?.let { "${TimeFormat.relative(it)}" } ?: "none yet",
                    valueColor = if (appStatus.lastNotification == null) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Last notification received per app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusCard(engine: EngineState, settings: PulseSettings) {
    PulseCard {
        SectionLabel("Activity")
        Spacer(Modifier.height(10.dp))
        LabeledValue(
            "Last pulse",
            if (engine.hasRun) {
                if (engine.lastTickSkipped) "${TimeFormat.relative(engine.lastTickTime)} (skipped)"
                else TimeFormat.relative(engine.lastTickTime)
            } else "—",
        )
        LabeledValue(
            "Next pulse",
            if (settings.engineEnabled && engine.nextTickTime > 0) {
                TimeFormat.relative(engine.nextTickTime)
            } else "—",
        )
        LabeledValue("Apps protected", settings.selectedPackages.size.toString())
        LabeledValue("Total pulses", engine.totalTicks.toString())
        if (engine.lastError.isNotEmpty()) {
            LabeledValue("Last error", engine.lastError, valueColor = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun QuickActions(
    onOpenHealth: () -> Unit,
    onOpenBattery: () -> Unit,
    onOpenLatency: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        QuickAction(Icons.Outlined.HealthAndSafety, "Health", Modifier.weight(1f), onOpenHealth)
        QuickAction(Icons.Outlined.BatteryChargingFull, "Battery", Modifier.weight(1f), onOpenBattery)
        QuickAction(Icons.Outlined.NetworkPing, "Latency", Modifier.weight(1f), onOpenLatency)
    }
}

@Composable
private fun QuickAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    androidx.compose.material3.ElevatedCard(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ShizukuWarningCard(status: ShizukuStatus, onOpenWizard: () -> Unit) {
    WarningCard(
        title = "Shizuku not connected",
        message = when (status) {
            ShizukuStatus.NOT_INSTALLED -> "Install Shizuku to enable privileged, no-root control."
            ShizukuStatus.NOT_RUNNING -> "Shizuku is installed but not running. Start it to continue."
            ShizukuStatus.PERMISSION_REQUIRED -> "Grant PulseGuard the Shizuku permission to continue."
            ShizukuStatus.READY -> ""
        },
        actionLabel = "Open setup",
        onAction = onOpenWizard,
    )
}

@Composable
private fun ExactAlarmWarningCard(onAction: () -> Unit) {
    WarningCard(
        title = "Exact alarms are off",
        message = "PulseGuard needs the exact-alarm permission for precise timing. Ticks may be delayed until it's granted.",
        actionLabel = "Fix in setup",
        onAction = onAction,
    )
}

@Composable
private fun EmptySelectionCard(onOpenApps: () -> Unit) {
    WarningCard(
        title = "No apps selected",
        message = "Pick the apps whose notifications keep arriving late.",
        actionLabel = "Choose apps",
        onAction = onOpenApps,
        tone = WarningTone.INFO,
    )
}

private enum class WarningTone { WARN, INFO }

@Composable
private fun WarningCard(
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    tone: WarningTone = WarningTone.WARN,
) {
    val container = when (tone) {
        WarningTone.WARN -> MaterialTheme.colorScheme.errorContainer
        WarningTone.INFO -> MaterialTheme.colorScheme.tertiaryContainer
    }
    val onContainer = when (tone) {
        WarningTone.WARN -> MaterialTheme.colorScheme.onErrorContainer
        WarningTone.INFO -> MaterialTheme.colorScheme.onTertiaryContainer
    }
    androidx.compose.material3.Surface(
        color = container,
        contentColor = onContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Warning, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(6.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

private fun plural(n: Int) = if (n == 1) "" else "s"
