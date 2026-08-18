package com.netsense.netpulse.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private fun colorScheme(palette: NetPulseColorPalette, dark: Boolean) = if (dark) {
    darkColorScheme(
        primary = palette.accent,
        onPrimary = palette.bg,
        primaryContainer = palette.accentContainer,
        onPrimaryContainer = palette.onAccentContainer,
        secondary = palette.textSecondary,
        onSecondary = palette.bg,
        secondaryContainer = palette.surfaceVariant,
        onSecondaryContainer = palette.textPrimary,
        tertiary = palette.accent,
        onTertiary = palette.bg,
        background = palette.bg,
        onBackground = palette.textPrimary,
        surface = palette.surface,
        onSurface = palette.textPrimary,
        surfaceVariant = palette.surfaceVariant,
        onSurfaceVariant = palette.textSecondary,
        outline = palette.border,
        outlineVariant = palette.borderSubtle
    )
} else {
    lightColorScheme(
        primary = palette.accent,
        onPrimary = palette.surface,
        primaryContainer = palette.accentContainer,
        onPrimaryContainer = palette.onAccentContainer,
        secondary = palette.textSecondary,
        onSecondary = palette.surface,
        secondaryContainer = palette.surfaceVariant,
        onSecondaryContainer = palette.textPrimary,
        tertiary = palette.accent,
        onTertiary = palette.surface,
        background = palette.bg,
        onBackground = palette.textPrimary,
        surface = palette.surface,
        onSurface = palette.textPrimary,
        surfaceVariant = palette.surfaceVariant,
        onSurfaceVariant = palette.textSecondary,
        outline = palette.border,
        outlineVariant = palette.borderSubtle
    )
}

/**
 * [darkTheme] defaults to the system Light/Dark setting - NetPulse doesn't yet have its own
 * in-app override, so it follows whatever the user already chose for their device.
 */
@Composable
fun NetPulseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val palette = if (darkTheme) DarkNetPulseColors else LightNetPulseColors
    CompositionLocalProvider(LocalNetPulseColors provides palette) {
        MaterialTheme(
            colorScheme = colorScheme(palette, darkTheme),
            typography = Typography,
            content = content
        )
    }
}
