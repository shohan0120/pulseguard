package com.pulseguard.ui.settings

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pulseguard.PulseGuardApp
import com.pulseguard.data.PulseSettings
import com.pulseguard.engine.EngineController
import com.pulseguard.ui.components.PulseCard
import com.pulseguard.ui.components.SectionLabel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as PulseGuardApp
    private val repo = app.settingsRepository

    val settings: StateFlow<PulseSettings?> =
        repo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setInterval(minutes: Int) = update { repo.setIntervalMinutes(minutes); reapply() }
    fun setSkipScreenOn(v: Boolean) = update { repo.setSkipWhenScreenOn(v) }
    fun setSkipCharging(v: Boolean) = update { repo.setSkipWhenCharging(v) }
    fun setSkipIdleWifi(v: Boolean) = update { repo.setSkipWhenIdleOnWifi(v) }
    fun setNightBackoff(v: Boolean) = update { repo.setNightBackoffEnabled(v); reapply() }
    fun setWhitelistSeconds(seconds: Int) = update { repo.setTempWhitelistSeconds(seconds) }

    fun setNightWindow(start: Int, end: Int) = update {
        repo.setNightWindow(((start % 24) + 24) % 24, ((end % 24) + 24) % 24)
        reapply()
    }

    private suspend fun reapply() = EngineController.reapplyIfRunning(app)

    private inline fun update(crossinline block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenApps: () -> Unit,
    onOpenWizard: () -> Unit,
    onOpenLimitations: () -> Unit,
    viewModel: SettingsViewModel = viewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val s = settings ?: return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        PulseCard {
            SectionLabel("Re-verify interval")
            Spacer(Modifier.height(10.dp))
            Text(
                "How often the background service re-checks and reapplies your protections. This is " +
                    "maintenance — the per-app OS settings are the actual fix.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                PulseSettings.INTERVAL_OPTIONS.forEachIndexed { index, minutes ->
                    SegmentedButton(
                        selected = s.intervalMinutes == minutes,
                        onClick = { viewModel.setInterval(minutes) },
                        shape = SegmentedButtonDefaults.itemShape(index, PulseSettings.INTERVAL_OPTIONS.size),
                    ) {
                        Text("$minutes min")
                    }
                }
            }
        }

        PulseCard {
            SectionLabel("Battery savers")
            Spacer(Modifier.height(4.dp))
            SwitchRow(
                "Skip while screen is on",
                "You're using the phone — push already flows.",
                s.skipWhenScreenOn,
                viewModel::setSkipScreenOn,
            )
            SwitchRow(
                "Skip while charging",
                "No need to save battery when plugged in.",
                s.skipWhenCharging,
                viewModel::setSkipCharging,
            )
            SwitchRow(
                "Skip when idle on Wi-Fi",
                "On unmetered Wi-Fi, push tends to survive on its own.",
                s.skipWhenIdleOnWifi,
                viewModel::setSkipIdleWifi,
            )
            SwitchRow(
                "Ease off at night",
                "Double the interval between ${twoDigit(s.nightStartHour)}:00 and ${twoDigit(s.nightEndHour)}:00.",
                s.nightBackoffEnabled,
                viewModel::setNightBackoff,
            )
            if (s.nightBackoffEnabled) {
                Spacer(Modifier.height(4.dp))
                NightWindowEditor(s, viewModel::setNightWindow)
            }
        }

        PulseCard {
            SectionLabel("Background poke (supplement)")
            Spacer(Modifier.height(10.dp))
            Text(
                "Each cycle also briefly temp-whitelists your apps. It's a minor supplement, not the " +
                    "fix — this is how long each app gets.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Stepper(
                label = "${s.tempWhitelistSeconds}s",
                onMinus = { viewModel.setWhitelistSeconds((s.tempWhitelistSeconds - 15).coerceAtLeast(15)) },
                onPlus = { viewModel.setWhitelistSeconds((s.tempWhitelistSeconds + 15).coerceAtMost(300)) },
            )
        }

        PulseCard {
            SectionLabel("Manage")
            Spacer(Modifier.height(4.dp))
            NavRow(Icons.Outlined.Apps, "Selected apps", "${s.selectedPackages.size} chosen", onOpenApps)
            NavRow(Icons.Outlined.Shield, "Shizuku setup", "Connect or re-check access", onOpenWizard)
            NavRow(Icons.Outlined.Info, "How PulseGuard works", "What it can and can't do", onOpenLimitations)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun NightWindowEditor(s: PulseSettings, onChange: (Int, Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("Start", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Stepper(
                label = "${twoDigit(s.nightStartHour)}:00",
                onMinus = { onChange(s.nightStartHour - 1, s.nightEndHour) },
                onPlus = { onChange(s.nightStartHour + 1, s.nightEndHour) },
            )
        }
        Column(Modifier.weight(1f)) {
            Text("End", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Stepper(
                label = "${twoDigit(s.nightEndHour)}:00",
                onMinus = { onChange(s.nightStartHour, s.nightEndHour - 1) },
                onPlus = { onChange(s.nightStartHour, s.nightEndHour + 1) },
            )
        }
    }
}

@Composable
private fun Stepper(label: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onMinus) {
            Icon(Icons.Outlined.Remove, contentDescription = "Decrease")
        }
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(72.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        IconButton(onClick = onPlus) {
            Icon(Icons.Outlined.Add, contentDescription = "Increase")
        }
    }
}

@Composable
private fun NavRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun twoDigit(n: Int): String = n.toString().padStart(2, '0')
