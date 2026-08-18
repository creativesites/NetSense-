package com.netsense.netpulse.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * All NetPulse-brand color tokens used across the UI, grouped into one palette so light and
 * dark can be swapped as a unit via [LocalNetPulseColors]. Every `NetPulseXxx` val below
 * resolves through this CompositionLocal, so existing call sites (`color = NetPulseBg`, etc.)
 * did not need to change anywhere else in the app to pick up dark mode.
 */
data class NetPulseColorPalette(
    val bg: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val border: Color,
    val borderSubtle: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val accentContainer: Color,
    val onAccentContainer: Color,
    val statusOptimal: Color,
    val statusOptimalBg: Color,
    val statusGood: Color,
    val statusGoodBg: Color,
    val statusDegraded: Color,
    val statusDegradedBg: Color,
    val statusPoor: Color,
    val statusPoorBg: Color,
    val statusUnusable: Color,
    val statusUnusableBg: Color,
    val statusZombie: Color,
    val statusZombieBg: Color
)

val LightNetPulseColors = NetPulseColorPalette(
    bg = Color(0xFFF8F9FA),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF1F3F5),
    border = Color(0xFFE5E7EB),
    borderSubtle = Color(0xFFF1F5F9),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF475569),
    textTertiary = Color(0xFF94A3B8),
    accent = Color(0xFF0284C7),
    accentContainer = Color(0xFFE0F2FE),
    onAccentContainer = Color(0xFF0369A1),
    statusOptimal = Color(0xFF10B981),
    statusOptimalBg = Color(0xFFECFDF5),
    statusGood = Color(0xFF0EA5E9),
    statusGoodBg = Color(0xFFF0F9FF),
    statusDegraded = Color(0xFFF59E0B),
    statusDegradedBg = Color(0xFFFEF3C7),
    statusPoor = Color(0xFFF97316),
    statusPoorBg = Color(0xFFFFEDD5),
    statusUnusable = Color(0xFFEF4444),
    statusUnusableBg = Color(0xFFFEE2E2),
    statusZombie = Color(0xFFDC2626),
    statusZombieBg = Color(0xFFFEF2F2)
)

val DarkNetPulseColors = NetPulseColorPalette(
    bg = Color(0xFF0B1120),
    surface = Color(0xFF111827),
    surfaceVariant = Color(0xFF1E293B),
    border = Color(0xFF334155),
    borderSubtle = Color(0xFF1E293B),
    textPrimary = Color(0xFFF1F5F9),
    textSecondary = Color(0xFF94A3B8),
    textTertiary = Color(0xFF64748B),
    accent = Color(0xFF38BDF8),
    accentContainer = Color(0xFF0C4A6E),
    onAccentContainer = Color(0xFF7DD3FC),
    statusOptimal = Color(0xFF34D399),
    statusOptimalBg = Color(0xFF064E3B),
    statusGood = Color(0xFF38BDF8),
    statusGoodBg = Color(0xFF0C4A6E),
    statusDegraded = Color(0xFFFBBF24),
    statusDegradedBg = Color(0xFF78350F),
    statusPoor = Color(0xFFFB923C),
    statusPoorBg = Color(0xFF7C2D12),
    statusUnusable = Color(0xFFF87171),
    statusUnusableBg = Color(0xFF7F1D1D),
    statusZombie = Color(0xFFF87171),
    statusZombieBg = Color(0xFF450A0A)
)

val LocalNetPulseColors = staticCompositionLocalOf { LightNetPulseColors }

val NetPulseBg: Color @Composable get() = LocalNetPulseColors.current.bg
val NetPulseSurface: Color @Composable get() = LocalNetPulseColors.current.surface
val NetPulseSurfaceVariant: Color @Composable get() = LocalNetPulseColors.current.surfaceVariant
val NetPulseBorder: Color @Composable get() = LocalNetPulseColors.current.border
val NetPulseBorderSubtle: Color @Composable get() = LocalNetPulseColors.current.borderSubtle

val NetPulseTextPrimary: Color @Composable get() = LocalNetPulseColors.current.textPrimary
val NetPulseTextSecondary: Color @Composable get() = LocalNetPulseColors.current.textSecondary
val NetPulseTextTertiary: Color @Composable get() = LocalNetPulseColors.current.textTertiary

val NetPulseAccent: Color @Composable get() = LocalNetPulseColors.current.accent
val NetPulseAccentContainer: Color @Composable get() = LocalNetPulseColors.current.accentContainer
val NetPulseOnAccentContainer: Color @Composable get() = LocalNetPulseColors.current.onAccentContainer

val StatusOptimal: Color @Composable get() = LocalNetPulseColors.current.statusOptimal
val StatusOptimalBg: Color @Composable get() = LocalNetPulseColors.current.statusOptimalBg

val StatusGood: Color @Composable get() = LocalNetPulseColors.current.statusGood
val StatusGoodBg: Color @Composable get() = LocalNetPulseColors.current.statusGoodBg

val StatusDegraded: Color @Composable get() = LocalNetPulseColors.current.statusDegraded
val StatusDegradedBg: Color @Composable get() = LocalNetPulseColors.current.statusDegradedBg

val StatusPoor: Color @Composable get() = LocalNetPulseColors.current.statusPoor
val StatusPoorBg: Color @Composable get() = LocalNetPulseColors.current.statusPoorBg

val StatusUnusable: Color @Composable get() = LocalNetPulseColors.current.statusUnusable
val StatusUnusableBg: Color @Composable get() = LocalNetPulseColors.current.statusUnusableBg

val StatusZombie: Color @Composable get() = LocalNetPulseColors.current.statusZombie
val StatusZombieBg: Color @Composable get() = LocalNetPulseColors.current.statusZombieBg
