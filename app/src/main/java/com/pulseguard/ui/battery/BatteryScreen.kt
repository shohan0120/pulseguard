package com.pulseguard.ui.battery

import android.app.Application
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
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pulseguard.PulseGuardApp
import com.pulseguard.engine.BatteryEstimate
import com.pulseguard.engine.BatteryInspector
import com.pulseguard.engine.BatteryStatsReading
import com.pulseguard.engine.DeviceStateProbe
import com.pulseguard.ui.components.LabeledValue
import com.pulseguard.ui.components.PulseCard
import com.pulseguard.ui.components.SectionLabel
import com.pulseguard.util.DeepLinks
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class BatteryViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as PulseGuardApp
    private val inspector = BatteryInspector(app, app.shizukuManager)
    private val probe = DeviceStateProbe(app)

    val estimate: StateFlow<BatteryEstimate?> =
        app.settingsRepository.settings
            .map { inspector.estimate(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _rawStats = MutableStateFlow<BatteryStatsReading?>(null)
    val rawStats: StateFlow<BatteryStatsReading?> = _rawStats

    private val _loadingStats = MutableStateFlow(false)
    val loadingStats: StateFlow<Boolean> = _loadingStats

    fun batteryLevel(): Int = probe.batteryLevelPercent()

    fun loadRawStats() {
        viewModelScope.launch {
            _loadingStats.value = true
            _rawStats.value = inspector.readRawStats()
            _loadingStats.value = false
        }
    }
}

@Composable
fun BatteryScreen(viewModel: BatteryViewModel = viewModel()) {
    val estimate by viewModel.estimate.collectAsStateWithLifecycle()
    val rawStats by viewModel.rawStats.collectAsStateWithLifecycle()
    val loadingStats by viewModel.loadingStats.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "Battery cost",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "PulseGuard is designed to be nearly free. Here's a transparent estimate of its own cost.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val est = estimate
        if (est == null) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            EstimateHeadline(est)
            BreakdownCard(est, viewModel.batteryLevel())
        }

        RawStatsCard(
            rawStats = rawStats,
            loading = loadingStats,
            onLoad = viewModel::loadRawStats,
        )

        PulseCard {
            SectionLabel("System settings")
            Spacer(Modifier.height(10.dp))
            Text(
                "See Android's own measurement, or exempt PulseGuard from battery optimization so its " +
                    "alarms are never deferred.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { DeepLinks.openAppDetails(context, context.packageName) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("PulseGuard battery usage")
            }
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(
                onClick = { DeepLinks.requestIgnoreBatteryOptimizationsForSelf(context) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Exempt PulseGuard from optimization")
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun EstimateHeadline(est: BatteryEstimate) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                formatPercent(est.estimatedDailyPercent),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "estimated battery per day",
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "≈ ${est.estimatedMahPerDay.roundToInt()} mAh · ${est.ticksPerDay} pulses/day",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun BreakdownCard(est: BatteryEstimate, batteryLevel: Int) {
    PulseCard {
        SectionLabel("How this is estimated")
        Spacer(Modifier.height(10.dp))
        LabeledValue("Apps protected", est.perAppCount.toString())
        LabeledValue("Daytime interval", "${est.effectiveDayIntervalMin} min")
        LabeledValue("Night interval", "${est.effectiveNightIntervalMin} min")
        LabeledValue("Pulses per day", est.ticksPerDay.toString())
        if (batteryLevel in 0..100) {
            LabeledValue("Current battery", "$batteryLevel%")
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "This is a worst-case model assuming every pulse runs. In practice, battery-saver skips " +
                "(screen on, charging) mean the real cost is usually lower.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RawStatsCard(
    rawStats: BatteryStatsReading?,
    loading: Boolean,
    onLoad: () -> Unit,
) {
    PulseCard {
        SectionLabel("Raw stats (Shizuku)")
        Spacer(Modifier.height(10.dp))
        Text(
            "Read Android's own batterystats for PulseGuard via Shizuku. A clean per-app mAh figure " +
                "isn't exposed on every ROM, so this is best-effort.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        when {
            loading -> CircularProgressIndicator(modifier = Modifier.size(28.dp))
            rawStats == null -> FilledTonalButton(onClick = onLoad) { Text("Read battery stats") }
            !rawStats.available -> {
                Text(rawStats.excerpt, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onLoad) { Text("Retry") }
            }
            else -> {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        rawStats.excerpt,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                        modifier = Modifier.padding(12.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onLoad) { Text("Refresh") }
            }
        }
    }
}

private fun formatPercent(percent: Double): String {
    return if (percent < 1.0) "<1%" else "≈${percent.roundToInt()}%"
}
