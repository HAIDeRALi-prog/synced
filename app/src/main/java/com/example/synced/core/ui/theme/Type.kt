@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.example.synced.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.synced.R

// Type pairing: bundled Crimson Pro (scholarly serif) for display, headline and
// title roles; Atkinson Hyperlegible for UI titles, body and labels — a face
// designed for low-vision readability. Fonts ship in the APK so the hierarchy
// renders offline. Atkinson is a static 400/700 family, so roles that specify a
// medium weight declare an explicit real weight instead. Labels carry extra
// tracking for the uppercase meta lines. Only M3 type roles are used — no
// per-screen font sizes.

private fun crimsonPro(weight: FontWeight) = FontFamily(
    Font(
        resId = R.font.crimson_pro,
        weight = weight,
        style = FontStyle.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
    ),
)

private val AtkinsonHyperlegible = FontFamily(
    Font(
        resId = R.font.atkinson_hyperlegible,
        weight = FontWeight.W400,
        style = FontStyle.Normal,
    ),
    Font(
        resId = R.font.atkinson_hyperlegible_bold,
        weight = FontWeight.W700,
        style = FontStyle.Normal,
    ),
)

private val CrimsonSemiBold = crimsonPro(FontWeight.W600)
private val CrimsonBold = crimsonPro(FontWeight.W700)

private val default = Typography()

val AppTypography = default.copy(
    displayLarge = default.displayLarge.copy(fontFamily = CrimsonBold),
    displayMedium = default.displayMedium.copy(fontFamily = CrimsonBold),
    displaySmall = default.displaySmall.copy(fontFamily = CrimsonSemiBold),
    headlineLarge = default.headlineLarge.copy(fontFamily = CrimsonSemiBold),
    headlineMedium = default.headlineMedium.copy(fontFamily = CrimsonSemiBold),
    headlineSmall = default.headlineSmall.copy(fontFamily = CrimsonSemiBold),
    titleLarge = default.titleLarge.copy(fontFamily = CrimsonSemiBold),
    titleMedium = default.titleMedium.copy(
        fontFamily = AtkinsonHyperlegible,
        fontWeight = FontWeight.W700,
    ),
    titleSmall = default.titleSmall.copy(
        fontFamily = AtkinsonHyperlegible,
        fontWeight = FontWeight.W700,
    ),
    bodyLarge = default.bodyLarge.copy(fontFamily = AtkinsonHyperlegible),
    bodyMedium = default.bodyMedium.copy(fontFamily = AtkinsonHyperlegible),
    bodySmall = default.bodySmall.copy(fontFamily = AtkinsonHyperlegible),
    labelLarge = default.labelLarge.copy(
        fontFamily = AtkinsonHyperlegible,
        fontWeight = FontWeight.W700,
        letterSpacing = 0.8.sp,
    ),
    labelMedium = default.labelMedium.copy(
        fontFamily = AtkinsonHyperlegible,
        fontWeight = FontWeight.W700,
        letterSpacing = 0.6.sp,
    ),
    labelSmall = default.labelSmall.copy(
        fontFamily = AtkinsonHyperlegible,
        fontWeight = FontWeight.W400,
        letterSpacing = 0.5.sp,
    ),
)
