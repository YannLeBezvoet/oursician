package com.oursician.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = OursicianAmber,
    secondary = OursicianTeal,
    background = OursicianBackground,
    surface = OursicianSurface,
    onBackground = OursicianOnBackground,
    onSurface = OursicianOnBackground,
    error = OursicianError,
)

private val LightColors = lightColorScheme(
    primary = OursicianAmber,
    secondary = OursicianTeal,
    error = OursicianError,
)

@Composable
fun OursicianTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
