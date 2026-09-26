package com.danielzuniga.player.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.danielzuniga.player.R

/** JetBrains Mono is the voice: titles, text, UI — everything. */
val MonoFamily = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_semibold, FontWeight.SemiBold),
    Font(R.font.jetbrains_mono_extrabold, FontWeight.ExtraBold),
)

/** Instrument Serif italic is the whisper: 1–3 words in a title, at most once per screen. */
val SerifFamily = FontFamily(Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic))

private fun mono(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0) = TextStyle(
    fontFamily = MonoFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.em,
    fontFeatureSettings = "calt",
)

/** The type scale from tokens.json, mapped onto Material roles. */
object DzType {
    val display = mono(56, 60, FontWeight.ExtraBold, -0.035)
    val h1 = mono(32, 40, FontWeight.SemiBold, -0.02)
    val h2 = mono(20, 28, FontWeight.SemiBold, -0.01)
    val body = mono(15, 26, FontWeight.Normal)
    val small = mono(13, 20, FontWeight.Normal)

    /** Eyebrows, tags and nav. Callers uppercase the text — the tokens never type capitals. */
    val label = mono(11, 16, FontWeight.Medium, 0.14)
    val whisper = TextStyle(
        fontFamily = SerifFamily,
        fontStyle = FontStyle.Italic,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 34.sp,
    )
}

val DzTypography = Typography(
    displayLarge = DzType.display,
    displayMedium = DzType.display.copy(fontSize = 44.sp, lineHeight = 48.sp),
    displaySmall = DzType.h1.copy(fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.03).em),
    headlineLarge = DzType.h1,
    headlineMedium = DzType.h1.copy(fontSize = 28.sp, lineHeight = 34.sp),
    headlineSmall = DzType.h1.copy(fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = DzType.h2,
    titleMedium = mono(16, 24, FontWeight.SemiBold, -0.01),
    titleSmall = mono(14, 20, FontWeight.SemiBold),
    bodyLarge = DzType.body,
    bodyMedium = DzType.small.copy(fontSize = 14.sp),
    bodySmall = DzType.small.copy(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = mono(13, 20, FontWeight.SemiBold, 0.01),
    labelMedium = DzType.small.copy(fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = DzType.label,
)
