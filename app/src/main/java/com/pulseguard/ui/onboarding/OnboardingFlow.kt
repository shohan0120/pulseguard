package com.pulseguard.ui.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier

/**
 * The pre-app onboarding: welcome → Shizuku wizard. Completion is signalled by the wizard
 * writing `onboardingCompleted`, which flips MainActivity over to the main app — so nothing
 * here needs to navigate on finish.
 */
@Composable
fun OnboardingFlow() {
    var step by rememberSaveable { mutableIntStateOf(0) }
    Scaffold { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (step) {
                0 -> WelcomeScreen(onGetStarted = { step = 1 })
                else -> ShizukuWizardScreen(isOnboarding = true, onFinished = { })
            }
        }
    }
}
