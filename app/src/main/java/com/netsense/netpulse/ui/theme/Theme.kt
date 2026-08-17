package com.netsense.netpulse.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val ModernLightColorScheme = lightColorScheme(
    primary = NetPulseAccent,
    onPrimary = NetPulseSurface,
    primaryContainer = NetPulseAccentContainer,
    onPrimaryContainer = NetPulseOnAccentContainer,
    secondary = NetPulseTextSecondary,
    onSecondary = NetPulseSurface,
    secondaryContainer = NetPulseSurfaceVariant,
    onSecondaryContainer = NetPulseTextPrimary,
    tertiary = NetPulseAccent,
    onTertiary = NetPulseSurface,
    background = NetPulseBg,
    onBackground = NetPulseTextPrimary,
    surface = NetPulseSurface,
    onSurface = NetPulseTextPrimary,
    surfaceVariant = NetPulseSurfaceVariant,
    onSurfaceVariant = NetPulseTextSecondary,
    outline = NetPulseBorder,
    outlineVariant = NetPulseBorderSubtle
)

@Composable
fun NetPulseTheme(
    darkTheme: Boolean = false, // Enforce clean, modern light aesthetic
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ModernLightColorScheme,
        typography = Typography,
        content = content
    )
}
