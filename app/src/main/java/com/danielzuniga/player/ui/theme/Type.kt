package com.danielzuniga.player.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.danielzuniga.player.R

/** Geometric grotesk for titles; gives headings and song names a distinct voice. */
val DisplayFamily = FontFamily(
    Font(R.font.space_grotesk_medium, FontWeight.Medium),
    Font(R.font.space_grotesk_bold, FontWeight.Bold),
)

/** Rounded, very legible sans for everything else. */
val BodyFamily = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_extrabold, FontWeight.ExtraBold),
)

private val base = Typography()

private fun TextStyle.display(weight: FontWeight = FontWeight.Bold, tracking: Double = -0.02) =
    copy(fontFamily = DisplayFamily, fontWeight = weight, letterSpacing = tracking.em)

private fun TextStyle.body(weight: FontWeight = FontWeight.Normal) =
    copy(fontFamily = BodyFamily, fontWeight = weight)

val AppTypography = Typography(
    displayLarge = base.displayLarge.display(),
    displayMedium = base.displayMedium.display(),
    displaySmall = base.displaySmall.display(),
    headlineLarge = base.headlineLarge.display(),
    headlineMedium = base.headlineMedium.display(),
    headlineSmall = base.headlineSmall.display(),
    titleLarge = base.titleLarge.display(),
    titleMedium = base.titleMedium.display(FontWeight.Medium, tracking = 0.0),
    titleSmall = base.titleSmall.body(FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.body(FontWeight.SemiBold).copy(fontSize = 15.sp),
    bodyMedium = base.bodyMedium.body(),
    bodySmall = base.bodySmall.body(),
    labelLarge = base.labelLarge.body(FontWeight.ExtraBold),
    labelMedium = base.labelMedium.body(FontWeight.SemiBold),
    labelSmall = base.labelSmall.body(FontWeight.ExtraBold).copy(letterSpacing = 0.12.em),
)
