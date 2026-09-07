package com.oursician.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val OursicianColors = lightColorScheme(
    primary = OursicianCoral,
    secondary = OursicianTurquoise,
    background = OursicianBackground,
    surface = OursicianSurface,
    onBackground = OursicianOnBackground,
    onSurface = OursicianOnBackground,
    error = OursicianError,
)

@Composable
fun OursicianTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = OursicianColors,
        typography = Typography,
        content = content,
    )
}
