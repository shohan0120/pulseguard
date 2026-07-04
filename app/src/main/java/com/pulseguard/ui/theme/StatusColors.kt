package com.pulseguard.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.pulseguard.engine.CheckState

/** Maps a [CheckState] to a theme-aware traffic-light color. */
@Composable
@ReadOnlyComposable
fun statusColor(state: CheckState): Color {
    val dark = isSystemInDarkTheme()
    return when (state) {
        CheckState.OK -> if (dark) statusOkDark else statusOkLight
        CheckState.WARN -> if (dark) statusWarnDark else statusWarnLight
        CheckState.FAIL -> if (dark) statusFailDark else statusFailLight
        CheckState.UNKNOWN -> if (dark) statusUnknownDark else statusUnknownLight
    }
}

@Composable
@ReadOnlyComposable
fun okColor(): Color = if (isSystemInDarkTheme()) statusOkDark else statusOkLight

@Composable
@ReadOnlyComposable
fun failColor(): Color = if (isSystemInDarkTheme()) statusFailDark else statusFailLight
