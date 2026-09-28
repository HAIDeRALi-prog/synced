package com.example.synced.core.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spacing scale — a single 4dp-based unit system (4/8/16/24/32) used for every
 * padding/margin in the app. Screens and components read from this instead of
 * declaring ad-hoc dp values.
 */
data class Spacing(
    /** 4dp — hairline gaps (icon↔label, title↔subtitle) */
    val xxs: Dp = 4.dp,
    /** 8dp — between list items, compact padding */
    val xs: Dp = 8.dp,
    /** 16dp — screen gutters, card internal padding */
    val sm: Dp = 16.dp,
    /** 24dp — section spacing */
    val md: Dp = 24.dp,
    /** 32dp — major block spacing */
    val lg: Dp = 32.dp,
)

val LocalSpacing = staticCompositionLocalOf { Spacing() }
