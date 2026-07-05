package com.pulseguard.ui.limitations

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pulseguard.ui.components.PulseCard
import com.pulseguard.ui.components.SectionLabel

/**
 * The honesty screen. States plainly what a no-root app can and cannot do on an aggressive China
 * ROM, so PulseGuard never overpromises.
 */
@Composable
fun LimitationsScreen() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "The honest version",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "PulseGuard doesn't have a magic fix for delayed notifications — nothing without root " +
                "does. Here's exactly what it does and doesn't do.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        PulseCard {
            SectionLabel("What actually fixes notifications")
            Spacer(Modifier.height(10.dp))
            Text(
                "On Xiaomi HyperOS / MIUI the real fix is the per-app OS settings: Autostart on, " +
                    "battery set to no-restrictions, background execution allowed, notifications and " +
                    "their channels enabled. Once those are right, notifications arrive. PulseGuard's job " +
                    "is to get them right and keep them that way.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        PulseCard {
            SectionLabel("What PulseGuard does")
            Spacer(Modifier.height(6.dp))
            Bullet("Checks the protections it can read (via Shizuku) and one-tap fixes them.")
            Bullet("Lists the ones it can't read (Autostart, background pop-up) as manual verify steps — it never fakes a status.")
            Bullet("Watches Shizuku and warns you the moment it drops (after a reboot), then reapplies fixable settings when it returns.")
            Bullet("Re-verifies periodically and reapplies a protection if it silently lapses after an update.")
            Bullet("Optionally shows when each app last received a notification, so silent failures surface.")
        }

        PulseCard {
            SectionLabel("What it can't do")
            Spacer(Modifier.height(6.dp))
            Bullet("It can't read or change MIUI Autostart — you must verify that yourself.")
            Bullet("The periodic background poke is a minor supplement, not the fix. Don't rely on it.")
            Bullet("After every reboot, Shizuku must be reactivated (unless your device is rooted) — until you do, PulseGuard is paused.")
        }

        PulseCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text("The only complete fixes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "If you want notifications that never lapse, the two real solutions are: root your device " +
                    "(so protections can't be stripped), or use the Global / EU ROM, which is far less " +
                    "aggressive than the China ROM. PulseGuard is the best a no-root China-ROM setup can do — " +
                    "not a replacement for either.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "No exaggeration, no dark patterns — just maintenance and honest warnings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun Bullet(text: String) {
    Row(modifier = Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        Text("•", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
