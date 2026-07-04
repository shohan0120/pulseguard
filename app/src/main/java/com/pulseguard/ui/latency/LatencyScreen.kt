package com.pulseguard.ui.latency

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
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulseguard.engine.LatencyResult
import com.pulseguard.engine.LatencyTester
import com.pulseguard.ui.components.PulseCard
import com.pulseguard.ui.components.SectionLabel
import com.pulseguard.util.TimeFormat

@Composable
fun LatencyScreen() {
    val context = LocalContext.current
    val results by LatencyTester.results.collectAsStateWithLifecycle()
    val running by LatencyTester.running.collectAsStateWithLifecycle()
    val latest = results.lastOrNull()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "Notification latency",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Fire a real test notification through Android's alarm + notification pipeline and measure " +
                "how long delivery actually takes. Watch this number stay low after setup.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        ResultHeadline(latest, running)

        Button(
            onClick = { LatencyTester.runTest(context) },
            enabled = !running,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (running) "Waiting for delivery…" else "Send test notification")
        }

        if (results.isNotEmpty()) {
            PulseCard {
                SectionLabel("History")
                Spacer(Modifier.height(8.dp))
                results.asReversed().forEach { result ->
                    HistoryRow(result)
                }
            }
        }

        PulseCard {
            SectionLabel("What am I looking at?")
            Spacer(Modifier.height(8.dp))
            Text(
                "A few hundred milliseconds or less means the pipeline is healthy. Seconds or minutes " +
                    "means the system is throttling background delivery — exactly what PulseGuard's " +
                    "pulses are there to prevent for your selected apps.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ResultHeadline(latest: LatencyResult?, running: Boolean) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(28.dp),
            contentAlignment = Alignment.Center,
        ) {
            when {
                running -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.height(12.dp))
                    Text("Measuring…", style = MaterialTheme.typography.titleMedium)
                }
                latest != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${latest.latencyMs} ms",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "last delivery latency",
                        style = MaterialTheme.typography.titleSmall,
                        textAlign = TextAlign.Center,
                    )
                }
                else -> Text(
                    "Send a test to see delivery latency",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun HistoryRow(result: LatencyResult) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            TimeFormat.clockSeconds(result.requestedAt),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "${result.latencyMs} ms",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
