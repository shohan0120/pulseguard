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
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.pulseguard.shizuku.ShizukuStatus
import com.pulseguard.ui.components.PulseCard
import com.pulseguard.ui.components.StatusDot
import com.pulseguard.ui.theme.statusColor
import com.pulseguard.util.DeepLinks
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

data class HealthUiState(
    val loading: Boolean = true,
    val apps: List<AppHealth> = emptyList(),
    val shizukuReady: Boolean = false,
    val selectedEmpty: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HealthViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as PulseGuardApp
    private val refreshTrigger = MutableStateFlow(0)

    val state: StateFlow<HealthUiState> =
        combine(
            app.settingsRepository.settings.map { it.selectedPackages }.distinctUntilChanged(),
            app.shizukuManager.status,
            refreshTrigger,
        ) { pkgs, shizuku, _ -> pkgs to shizuku }
            .mapLatest { (pkgs, shizuku) ->
                if (pkgs.isEmpty()) {
                    HealthUiState(loading = false, selectedEmpty = true, shizukuReady = shizuku.isReady)
                } else {
                    val results = pkgs.sorted().map { app.healthChecker.check(it) }
                    HealthUiState(loading = false, apps = results, shizukuReady = shizuku.isReady)
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HealthUiState(loading = true))

    fun refresh() {
        refreshTrigger.value += 1
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Health dashboard",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (state.shizukuReady) "Live checks via Shizuku"
                    else "Guided-only mode — connect Shizuku for live checks",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = viewModel::refresh) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
            }
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
                item { AutostartGuidanceCard(context) }
                items(state.apps, key = { it.packageName }) { health ->
                    AppHealthCard(health, context)
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun AppHealthCard(health: AppHealth, context: android.content.Context) {
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
            CheckRow(check) { target ->
                DeepLinks.openFixTarget(context, target, health.packageName)
            }
        }
    }
}

@Composable
private fun CheckRow(check: HealthCheck, onFix: (FixTarget) -> Unit) {
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
            if (check.fixTarget != null) {
                TextButton(
                    onClick = { onFix(check.fixTarget) },
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
                ) {
                    Text("Fix")
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        }
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
                    "Autostart (MIUI / HyperOS)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Autostart can't be read by any app, so we can't show a status for it. But it's the " +
                    "single most important setting on Xiaomi devices: enable Autostart for each of your " +
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
