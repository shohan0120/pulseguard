package com.pulseguard.ui.home

import android.app.Application
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NetworkPing
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.pulseguard.engine.AppHealth
import com.pulseguard.engine.CheckState
import com.pulseguard.engine.EngineController
import com.pulseguard.engine.EngineState
import com.pulseguard.engine.FixTarget
import com.pulseguard.engine.HealthCheck
import com.pulseguard.shizuku.ShizukuStatus
import com.pulseguard.ui.components.PulseCard
import com.pulseguard.ui.components.StatusDot
import com.pulseguard.ui.theme.failColor
import com.pulseguard.ui.theme.okColor
import com.pulseguard.ui.theme.statusColor
import com.pulseguard.util.DeepLinks
import com.pulseguard.util.TimeFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppProtection(
    val health: AppHealth,
    val lastNotification: Long?,
)

data class DashboardUiState(
    val settings: PulseSettings,
    val shizukuStatus: ShizukuStatus,
    val engineState: EngineState,
    val apps: List<AppProtection>,
    val checking: Boolean,
    val fixing: Set<String>,
)

private data class ReactiveInputs(
    val settings: PulseSettings,
    val shizukuStatus: ShizukuStatus,
    val engineState: EngineState,
    val lastSeen: Map<String, Long>,
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as PulseGuardApp

    private val _health = MutableStateFlow<List<AppHealth>>(emptyList())
    private val _checking = MutableStateFlow(true)
    private val _fixing = MutableStateFlow<Set<String>>(emptySet())
    private val selected =
        app.settingsRepository.settings.map { it.selectedPackages }.distinctUntilChanged()

    private val reactive = combine(
        app.settingsRepository.settings,
        app.shizukuManager.status,
        app.engineStateRepository.state,
        app.notificationLogRepository.lastSeen,
    ) { s, sh, e, seen -> ReactiveInputs(s, sh, e, seen) }

    val uiState: StateFlow<DashboardUiState?> =
        combine(reactive, _health, _checking, _fixing) { r, health, checking, fixing ->
            val apps = r.settings.selectedPackages.sorted().map { pkg ->
                val h = health.find { it.packageName == pkg }
                    ?: AppHealth(pkg, app.appRepository.labelFor(pkg), emptyList(), false)
                AppProtection(h, r.lastSeen[pkg])
            }
            DashboardUiState(r.settings, r.shizukuStatus, r.engineState, apps, checking, fixing)
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch { selected.collect { runAllChecks() } }
    }

    fun runAllChecks() {
        viewModelScope.launch {
            val pkgs = app.settingsRepository.snapshot().selectedPackages.sorted()
            _checking.value = true
            _health.value = pkgs.map { app.healthChecker.check(it) }
            _checking.value = false
        }
    }

    fun autoFix(packageName: String, checkId: String) {
        viewModelScope.launch {
            val key = "$packageName#$checkId"
            _fixing.update { it + key }
            app.healthChecker.autoFix(packageName, checkId)
            val rechecked = app.healthChecker.check(packageName)
            _health.update { list -> list.map { if (it.packageName == packageName) rechecked else it } }
            _fixing.update { it - key }
        }
    }

    fun setEngineEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) EngineController.enable(app) else EngineController.disable(app)
        }
    }

    fun refreshShizuku() = app.shizukuManager.refresh()
    fun isNotificationAccessGranted(): Boolean = DeepLinks.isNotificationAccessGranted(app)
}

@Composable
fun HomeScreen(
    onOpenWizard: () -> Unit,
    onOpenApps: () -> Unit,
    onOpenBattery: () -> Unit,
    onOpenLatency: () -> Unit,
    onOpenLimitations: () -> Unit,
    viewModel: DashboardViewModel = viewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var notifAccess by remember { mutableStateOf(viewModel.isNotificationAccessGranted()) }
    LifecycleResumeEffect(Unit) {
        viewModel.refreshShizuku()
        notifAccess = viewModel.isNotificationAccessGranted()
        onPauseOrDispose { }
    }

    val ui = state ?: return
    val shizukuReady = ui.shizukuStatus == ShizukuStatus.READY

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("Protection", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "The real fix is your per-app system settings. PulseGuard keeps them in place and " +
                    "warns you when they lapse.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            StatusCard(
                enabled = ui.settings.engineEnabled,
                shizukuReady = shizukuReady,
                appCount = ui.settings.selectedPackages.size,
                onToggle = viewModel::setEngineEnabled,
                onReactivate = onOpenWizard,
            )
        }

        if (!shizukuReady) {
            item { ShizukuWarningCard(ui.shizukuStatus, onOpenWizard) }
        }

        if (ui.settings.selectedPackages.isEmpty()) {
            item { EmptySelectionCard(onOpenApps) }
        } else {
            if (!notifAccess) {
                item {
                    NotificationAccessCard(onEnable = { DeepLinks.openNotificationAccessSettings(context) })
                }
            }
            item {
                FilledTonalButton(
                    onClick = viewModel::runAllChecks,
                    enabled = !ui.checking,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (ui.checking) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.AutoMirrored.Filled.PlaylistAddCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(if (ui.checking) "Checking…" else "Run all checks")
                }
            }
            items(ui.apps, key = { it.health.packageName }) { protection ->
                AppProtectionCard(
                    protection = protection,
                    shizukuReady = shizukuReady,
                    showLastNotification = notifAccess,
                    fixing = ui.fixing,
                    onAutoFix = viewModel::autoFix,
                    onFix = { target, pkg -> DeepLinks.openFixTarget(context, target, pkg) },
                )
            }
        }

        item { LimitationsLink(onOpenLimitations) }
        item {
            QuickActions(onOpenBattery = onOpenBattery, onOpenLatency = onOpenLatency)
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun StatusCard(
    enabled: Boolean,
    shizukuReady: Boolean,
    appCount: Int,
    onToggle: (Boolean) -> Unit,
    onReactivate: () -> Unit,
) {
    val paused = enabled && !shizukuReady
    val active = enabled && shizukuReady
    val accent by animateColorAsState(
        when {
            active -> okColor()
            paused -> failColor()
            else -> MaterialTheme.colorScheme.outline
        },
        label = "accent",
    )
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
                        active -> "Protected"
                        else -> "Maintenance off"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (paused) accent else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    when {
                        paused -> "Shizuku needs reactivation"
                        active -> "Maintaining $appCount app${plural(appCount)} in the background"
                        else -> "Background watchdog & re-verify are off"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
        if (paused) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onReactivate, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Reactivate Shizuku")
            }
        }
    }
}

@Composable
private fun AppProtectionCard(
    protection: AppProtection,
    shizukuReady: Boolean,
    showLastNotification: Boolean,
    fixing: Set<String>,
    onAutoFix: (String, String) -> Unit,
    onFix: (FixTarget, String) -> Unit,
) {
    val health = protection.health
    PulseCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(statusColor(health.overall), size = 14)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(health.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (showLastNotification) {
                    Text(
                        "Last notification: " +
                            (protection.lastNotification?.let { TimeFormat.relative(it) } ?: "none yet"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        health.checks.forEach { check ->
            LayerRow(
                check = check,
                packageName = health.packageName,
                shizukuReady = shizukuReady,
                isFixing = fixing.contains("${health.packageName}#${check.id}"),
                onAutoFix = { onAutoFix(health.packageName, check.id) },
                onFix = { target -> onFix(target, health.packageName) },
            )
        }
    }
}

@Composable
private fun LayerRow(
    check: HealthCheck,
    packageName: String,
    shizukuReady: Boolean,
    isFixing: Boolean,
    onAutoFix: () -> Unit,
    onFix: (FixTarget) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(modifier = Modifier.padding(top = 5.dp)) {
            StatusDot(statusColor(check.state), size = 10)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(check.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(8.dp))
                Text(
                    stateWord(check.state),
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor(check.state),
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                check.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when {
                check.isManual && check.fixTarget != null ->
                    ActionButton("Verify", Icons.AutoMirrored.Outlined.OpenInNew) { onFix(check.fixTarget) }

                check.state == CheckState.OK -> Unit

                isFixing -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Fixing…", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                shizukuReady && check.autoFixable -> Row(verticalAlignment = Alignment.CenterVertically) {
                    ActionButton("Fix automatically", Icons.Filled.AutoFixHigh, onClick = onAutoFix)
                    if (check.fixTarget != null) {
                        Spacer(Modifier.width(10.dp))
                        ActionButton("Fix in Settings", Icons.AutoMirrored.Outlined.OpenInNew) { onFix(check.fixTarget) }
                    }
                }

                check.fixTarget != null ->
                    ActionButton("Fix in Settings", Icons.AutoMirrored.Outlined.OpenInNew) { onFix(check.fixTarget) }
            }
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(label)
    }
}

@Composable
private fun NotificationAccessCard(onEnable: () -> Unit) {
    PulseCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.NotificationsActive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text("Delivery proof (optional)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Grant Notification access and each app below will show when it last received a " +
                "notification — package name and time only, never any content.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        FilledTonalButton(onClick = onEnable) { Text("Enable notification access") }
    }
}

@Composable
private fun LimitationsLink(onOpen: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpen,
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Info, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("How PulseGuard works — and its limits", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "What a no-root app can and can't do on HyperOS.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun QuickActions(onOpenBattery: () -> Unit, onOpenLatency: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        QuickAction(Icons.Outlined.BatteryChargingFull, "Battery cost", Modifier.weight(1f), onOpenBattery)
        QuickAction(Icons.Outlined.NetworkPing, "Latency test", Modifier.weight(1f), onOpenLatency)
    }
}

@Composable
private fun QuickAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    ElevatedCard(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(18.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
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
            ShizukuStatus.NOT_INSTALLED -> "Install Shizuku to enable the checks and one-tap fixes."
            ShizukuStatus.NOT_RUNNING -> "Shizuku is installed but not running. Start it to continue."
            ShizukuStatus.PERMISSION_REQUIRED -> "Grant PulseGuard the Shizuku permission to continue."
            ShizukuStatus.READY -> ""
        },
        actionLabel = "Open setup",
        onAction = onOpenWizard,
    )
}

@Composable
private fun EmptySelectionCard(onOpenApps: () -> Unit) {
    WarningCard(
        title = "No apps selected",
        message = "Pick the apps whose notifications keep arriving late.",
        actionLabel = "Choose apps",
        onAction = onOpenApps,
        info = true,
    )
}

@Composable
private fun WarningCard(
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    info: Boolean = false,
) {
    Surface(
        color = if (info) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.errorContainer,
        contentColor = if (info) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onErrorContainer,
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

private fun stateWord(state: CheckState): String = when (state) {
    CheckState.OK -> "OK"
    CheckState.WARN -> "Check"
    CheckState.FAIL -> "Problem"
    CheckState.UNKNOWN -> "Unknown"
    CheckState.MANUAL -> "Verify"
}

private fun plural(n: Int) = if (n == 1) "" else "s"
