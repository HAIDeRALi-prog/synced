package com.example.synced.core.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// "Ink & Slate" — scholarly navy + citation gold on paper
// (design-system/synced/MASTER.md). Contrast pairs meet WCAG AA:
// 4.5:1 for body text, 3:1 for large text and non-text UI, in both modes.

// Light seeds
private val Navy = Color(0xFF1E3A5F) // primary navy / ring
private val NavyForeground = Color(0xFF0F172A)
private val Paper = Color(0xFFF8FAFC)
private val CardWhite = Color(0xFFFFFFFF)
private val Muted = Color(0xFFE9EEF5)
private val MutedForeground = Color(0xFF475569)
private val Hairline = Color(0xFFCBD5E1)
private val Gold = Color(0xFFB45309) // filled accent (white label on top, 5.0:1)
private val GoldTint = Color(0xFFFEF3C7)
private val GoldTintForeground = Color(0xFF78350F)
private val Danger = Color(0xFFDC2626)

// Dark seeds (slate base from colors.csv "Digital Signage" dark palette,
// gold lightened to #F59E0B so accent text keeps 4.5:1 on near-black)
private val NightPaper = Color(0xFF020617)
private val NightCard = Color(0xFF0E1223)
private val NightMuted = Color(0xFF1A1E2F)
private val NightForeground = Color(0xFFF8FAFC)
private val NightMutedForeground = Color(0xFF94A3B8)
private val NightHairline = Color(0xFF334155)
private val NightOutline = Color(0xFF64748B)
private val NightGold = Color(0xFFF59E0B) // accent on dark (9:1)
private val NightGoldContainer = Color(0xFF3F2D0F)
private val NightGoldContainerForeground = Color(0xFFFDE68A)
private val NightDanger = Color(0xFFFFB4AB)

internal val LightColorScheme = lightColorScheme(
    primary = Gold,
    onPrimary = Color.White,
    primaryContainer = Muted,
    onPrimaryContainer = Navy,
    secondary = Navy,
    onSecondary = Color.White,
    secondaryContainer = Muted,
    onSecondaryContainer = Navy,
    tertiary = Gold, // accent text / numbered slug on paper (4.8:1)
    onTertiary = Color.White,
    tertiaryContainer = GoldTint,
    onTertiaryContainer = GoldTintForeground,
    background = Paper,
    onBackground = NavyForeground,
    surface = CardWhite,
    onSurface = NavyForeground,
    surfaceVariant = Muted,
    onSurfaceVariant = MutedForeground,
    surfaceTint = Navy,
    outline = NightOutline,
    outlineVariant = Hairline,
    error = Danger,
    onError = Color.White,
    errorContainer = Color(0xFFFFE4E4),
    onErrorContainer = Color(0xFF7F1D1D),
    inverseSurface = Navy,
    inverseOnSurface = Paper,
    inversePrimary = Gold,
)

internal val DarkColorScheme = darkColorScheme(
    primary = NightGold,
    onPrimary = NavyForeground,
    primaryContainer = NightGoldContainer,
    onPrimaryContainer = NightGoldContainerForeground,
    secondary = Color(0xFFCBD5E1),
    onSecondary = NavyForeground,
    secondaryContainer = NightMuted,
    onSecondaryContainer = Color(0xFFE2E8F0),
    tertiary = NightGold, // accent text on dark (9:1)
    onTertiary = NavyForeground,
    tertiaryContainer = NightGoldContainer,
    onTertiaryContainer = NightGoldContainerForeground,
    background = NightPaper,
    onBackground = NightForeground,
    surface = NightCard,
    onSurface = NightForeground,
    surfaceVariant = NightMuted,
    onSurfaceVariant = NightMutedForeground,
    surfaceTint = NightGold,
    outline = NightOutline,
    outlineVariant = NightHairline,
    error = NightDanger,
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = NightForeground,
    inverseOnSurface = NightPaper,
    inversePrimary = Gold,
)
