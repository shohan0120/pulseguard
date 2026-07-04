package com.pulseguard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulseguard.ui.PulseGuardRoot
import com.pulseguard.ui.onboarding.OnboardingFlow
import com.pulseguard.ui.theme.PulseGuardTheme
import kotlinx.coroutines.flow.map

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* best-effort */ }

    // A one-shot route to navigate to on launch (e.g. from the "Shizuku paused" notification).
    private var pendingRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        maybeRequestNotificationPermission()
        pendingRoute = intent?.getStringExtra(EXTRA_ROUTE)

        setContent {
            PulseGuardTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val app = application as PulseGuardApp
                    // remember the mapped flow so recomposition doesn't re-subscribe DataStore.
                    val onboardedFlow = remember(app) {
                        app.settingsRepository.settings.map { it.onboardingCompleted }
                    }
                    val onboarded by onboardedFlow.collectAsStateWithLifecycle(initialValue = null)

                    when (onboarded) {
                        null -> LoadingScreen()
                        true -> PulseGuardRoot(
                            deepLinkRoute = pendingRoute,
                            onDeepLinkConsumed = { pendingRoute = null },
                        )
                        false -> OnboardingFlow()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingRoute = intent.getStringExtra(EXTRA_ROUTE)
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    companion object {
        const val EXTRA_ROUTE = "com.pulseguard.extra.ROUTE"
        const val ROUTE_SHIZUKU_WIZARD = "shizuku_wizard"
    }
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
