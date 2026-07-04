package com.pulseguard.ui.health

import android.app.Application
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
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pulseguard.PulseGuardApp
import com.pulseguard.engine.AppHealth
import com.pulseguard.engine.CheckState
import com.pulseguard.engine.FixTarget
import com.pulseguard.engine.HealthCheck
import com.pulseguard.ui.components.PulseCard
import com.pulseguard.ui.components.StatusDot
import com.pulseguard.ui.theme.statusColor
import com.pulseguard.util.DeepLinks
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HealthUiState(
    val loading: Boolean = true,
    val running: Boolean = false,
    val apps: List<AppHealth> = emptyList(),
    val fixing: Set<String> = emptySet(), // "pkg#checkId"
    val shizukuReady: Boolean = false,
    val selectedEmpty: Boolean = false,
)

class HealthViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as PulseGuardApp

    private val _apps = MutableStateFlow<List<AppHealth>>(emptyList())
    private val _running = MutableStateFlow(true)
    private val _fixing = MutableStateFlow<Set<String>>(emptySet())
    private val selected =
        app.settingsRepository.settings.map { it.selectedPackages }.distinctUntilChanged()

    val state: StateFlow<HealthUiState> =
        combine(_apps, _running, _fixing, app.shizukuManager.status, selected) { apps, running, fixing, shizuku, sel ->
            HealthUiState(
                loading = running && apps.isEmpty(),
                running = running,
                apps = apps,
                fixing = fixing,
                shizukuReady = shizuku.isReady,
                selectedEmpty = sel.isEmpty(),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HealthUiState(loading = true))

    init {
        // Re-run checks whenever the protected-app selection changes (and once on start).
        viewModelScope.launch { selected.collect { runAllChecks() } }
    }

    fun runAllChecks() {
        viewModelScope.launch {
            val pkgs = app.settingsRepository.snapshot().selectedPackages.sorted()
            _running.value = true
            _apps.value = pkgs.map { app.healthChecker.check(it) }
            _running.value = false
        }
    }

    fun autoFix(packageName: String, checkId: String) {
        viewModelScope.launch {
            val key = "$packageName#$checkId"
            _fixing.update { it + key }
            app.healthChecker.autoFix(packageName, checkId)
            val rechecked = app.healthChecker.check(packageName)
            _apps.update { list -> list.map { if (it.packageName == packageName) rechecked else it } }
            _fixing.update { it - key }
        }
    }
}

@Composable
fun HealthScreen(
    onOpenApps: () -> Unit,
    viewModel: HealthViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
            Text(
                "Health check",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                if (state.shizukuReady) "Live checks & one-tap fixes via Shizuku"
                else "Guided-only mode — connect Shizuku for live checks and auto-fixes",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.selectedEmpty -> EmptyHealth(onOpenApps)
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    FilledTonalButton(
                        onClick = viewModel::runAllChecks,
                        enabled = !state.running,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (state.running) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(Icons.AutoMirrored.Filled.PlaylistAddCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(if (state.running) "Checking…" else "Run all checks")
                    }
                }
                item { AutostartGuidanceCard(context) }
                items(state.apps, key = { it.packageName }) { health ->
                    AppHealthCard(
                        health = health,
                        shizukuReady = state.shizukuReady,
                        fixing = state.fixing,
                        onAutoFix = viewModel::autoFix,
                        onManualFix = { target -> DeepLinks.openFixTarget(context, target, health.packageName) },
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun AppHealthCard(
    health: AppHealth,
    shizukuReady: Boolean,
    fixing: Set<String>,
    onAutoFix: (String, String) -> Unit,
    onManualFix: (FixTarget) -> Unit,
) {
    PulseCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(statusColor(health.overall), size = 14)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(health.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    health.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        health.checks.forEach { check ->
            CheckRow(
                check = check,
                isFixing = fixing.contains("${health.packageName}#${check.id}"),
                canAutoFix = shizukuReady && check.autoFixable,
                onAutoFix = { onAutoFix(health.packageName, check.id) },
                onManualFix = onManualFix,
            )
        }
    }
}

@Composable
private fun CheckRow(
    check: HealthCheck,
    isFixing: Boolean,
    canAutoFix: Boolean,
    onAutoFix: () -> Unit,
    onManualFix: (FixTarget) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
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
            if (check.state != CheckState.OK) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when {
                        isFixing -> {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Fixing…",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        canAutoFix -> {
                            TextButton(
                                onClick = onAutoFix,
                                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
                            ) {
                                Icon(Icons.Filled.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Fix automatically")
                            }
                            if (check.fixTarget != null) {
                                Spacer(Modifier.width(12.dp))
                                ManualFixButton(check.fixTarget, onManualFix)
                            }
                        }

                        check.fixTarget != null -> ManualFixButton(check.fixTarget, onManualFix)
                    }
                }
            }
        }
    }
}

@Composable
private fun ManualFixButton(target: FixTarget, onManualFix: (FixTarget) -> Unit) {
    TextButton(
        onClick = { onManualFix(target) },
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
    ) {
        Text("Fix in Settings")
        Spacer(Modifier.width(4.dp))
        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun AutostartGuidanceCard(context: android.content.Context) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.RocketLaunch, contentDescription = null)
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    "Verify Autostart (MIUI / HyperOS)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Autostart can't be read by any app, so we don't show a status or auto-fix it. But it's " +
                    "the single most important setting on Xiaomi devices: enable Autostart for each of your " +
                    "selected apps, or they'll be killed regardless.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = { DeepLinks.openAutostart(context, context.packageName) }) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Open Autostart settings")
            }
        }
    }
}

@Composable
private fun EmptyHealth(onOpenApps: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "No apps to check yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Select some apps first and their health will appear here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onOpenApps) { Text("Choose apps") }
        }
    }
}

private fun stateWord(state: CheckState): String = when (state) {
    CheckState.OK -> "OK"
    CheckState.WARN -> "Check"
    CheckState.FAIL -> "Problem"
    CheckState.UNKNOWN -> "Unknown"
}
