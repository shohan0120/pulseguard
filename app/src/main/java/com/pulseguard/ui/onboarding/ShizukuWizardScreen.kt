package com.pulseguard.ui.onboarding

import android.app.Application
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.pulseguard.shizuku.ShizukuStatus
import com.pulseguard.ui.components.PulseCard
import com.pulseguard.util.DeepLinks
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ShizukuViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as PulseGuardApp

    val status: StateFlow<ShizukuStatus> = app.shizukuManager.status

    fun refresh() = app.shizukuManager.refresh()
    fun requestPermission() = app.shizukuManager.requestPermission()
    fun completeOnboarding() {
        viewModelScope.launch { app.settingsRepository.setOnboardingCompleted(true) }
    }
}

@Composable
fun ShizukuStatusPill(status: ShizukuStatus, modifier: Modifier = Modifier) {
    val (container, content, label) = when (status) {
        ShizukuStatus.READY -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            "Shizuku connected ✅",
        )
        else -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            "Shizuku not connected ❌",
        )
    }
    androidx.compose.material3.Surface(
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(50),
        modifier = modifier,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
fun ShizukuWizardScreen(
    isOnboarding: Boolean,
    onFinished: () -> Unit,
    viewModel: ShizukuViewModel = viewModel(),
) {
    val context = LocalContext.current
    val status by viewModel.status.collectAsStateWithLifecycle()

    // Re-check Shizuku whenever the user returns from the Shizuku app / settings.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    val installed = status != ShizukuStatus.NOT_INSTALLED
    val started = status == ShizukuStatus.PERMISSION_REQUIRED || status == ShizukuStatus.READY
    val granted = status == ShizukuStatus.READY

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "Set up Shizuku",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Shizuku gives PulseGuard the privileged, no-root access it needs to wake your apps. " +
                "It takes about 3 minutes to set up once.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ShizukuStatusPill(status)

        StepCard(
            number = 1,
            title = "Install Shizuku",
            body = if (installed) {
                "Shizuku is installed. It's the bridge that grants PulseGuard shell access."
            } else {
                "Shizuku isn't on the Play Store — download the APK from GitHub releases or F-Droid, " +
                    "then open it to install (you may need to allow \"install unknown apps\" for your " +
                    "browser or file manager). This screen updates automatically once it's installed."
            },
            done = installed,
            actionLabel = if (installed) "Installed" else "GitHub releases",
            actionEnabled = !installed,
            onAction = { DeepLinks.openShizukuGitHubReleases(context) },
            secondaryLabel = if (installed) null else "F-Droid",
            onSecondary = { DeepLinks.openShizukuFDroid(context) },
        )

        StepCard(
            number = 2,
            title = "Enable Wireless Debugging",
            body = "In Developer options, turn on Wireless debugging (tap Build number 7× to unlock " +
                "Developer options first). Shizuku uses it to start without a computer.",
            done = started,
            actionLabel = "Open Developer options",
            actionEnabled = true,
            secondaryLabel = "How it works",
            onSecondary = { DeepLinks.openShizukuGuide(context) },
            onAction = { DeepLinks.openWirelessDebugging(context) },
        )

        StepCard(
            number = 3,
            title = "Start Shizuku",
            body = "Open the Shizuku app and tap “Start” (via Wireless debugging pairing). The status " +
                "above turns green once it's running.",
            done = started,
            actionLabel = if (started) "Shizuku is running" else "Open Shizuku",
            actionEnabled = !started,
            onAction = {
                if (!DeepLinks.openShizuku(context)) DeepLinks.openShizukuGitHubReleases(context)
            },
        )

        StepCard(
            number = 4,
            title = "Grant PulseGuard access",
            body = "Approve PulseGuard in the Shizuku permission prompt. This is what lets us run the " +
                "keep-alive commands.",
            done = granted,
            actionLabel = if (granted) "Access granted" else "Grant permission",
            actionEnabled = started && !granted,
            onAction = { viewModel.requestPermission() },
        )

        Spacer(Modifier.height(4.dp))

        if (isOnboarding) {
            Button(
                onClick = {
                    viewModel.completeOnboarding()
                    onFinished()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (granted) "Finish setup" else "Skip for now")
            }
            if (!granted) {
                Text(
                    "You can finish setup later — PulseGuard runs in a limited guided-only mode until " +
                        "Shizuku is connected.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Button(onClick = onFinished, modifier = Modifier.fillMaxWidth()) {
                Text("Done")
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun StepCard(
    number: Int,
    title: String,
    body: String,
    done: Boolean,
    actionLabel: String,
    actionEnabled: Boolean,
    onAction: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    PulseCard {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        if (done) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (done) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = "Done",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                } else {
                    Text(
                        number.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onAction, enabled = actionEnabled) {
                        Text(actionLabel)
                    }
                    if (secondaryLabel != null && onSecondary != null) {
                        androidx.compose.material3.TextButton(onClick = onSecondary) {
                            Text(secondaryLabel)
                        }
                    }
                }
            }
        }
    }
}
