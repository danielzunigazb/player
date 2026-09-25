package com.danielzuniga.player.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.danielzuniga.player.data.ThemeMode

// "Ember" identity: warm coral accent over violet-tinted ink, used when no dynamic source applies.
private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF8A68),
    onPrimary = Color(0xFF3A0B00),
    primaryContainer = Color(0xFF5C1F0E),
    onPrimaryContainer = Color(0xFFFFDBD0),
    secondary = Color(0xFFB4A8FF),
    onSecondary = Color(0xFF221768),
    secondaryContainer = Color(0xFF362B7A),
    onSecondaryContainer = Color(0xFFE4DFFF),
    tertiary = Color(0xFF6FE3BE),
    onTertiary = Color(0xFF00382A),
    background = Color(0xFF0E0C13),
    onBackground = Color(0xFFEDE7F1),
    surface = Color(0xFF0E0C13),
    onSurface = Color(0xFFEDE7F1),
    surfaceVariant = Color(0xFF2A2533),
    onSurfaceVariant = Color(0xFFB9B1C4),
    surfaceContainerLowest = Color(0xFF09080D),
    surfaceContainerLow = Color(0xFF15121B),
    surfaceContainer = Color(0xFF1A1721),
    surfaceContainerHigh = Color(0xFF221E2A),
    surfaceContainerHighest = Color(0xFF2B2634),
    outline = Color(0xFF6E6679),
    outlineVariant = Color(0xFF3A3443),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFFD9481F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBD0),
    onPrimaryContainer = Color(0xFF3A0B00),
    secondary = Color(0xFF5B4BD6),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE4DFFF),
    onSecondaryContainer = Color(0xFF1A0F5C),
    tertiary = Color(0xFF00805F),
    background = Color(0xFFFBF7F3),
    onBackground = Color(0xFF1D1A20),
    surface = Color(0xFFFBF7F3),
    onSurface = Color(0xFF1D1A20),
    surfaceVariant = Color(0xFFEDE5EA),
    onSurfaceVariant = Color(0xFF5D5563),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF6F0EE),
    surfaceContainer = Color(0xFFF1EAE8),
    surfaceContainerHigh = Color(0xFFEBE3E2),
    surfaceContainerHighest = Color(0xFFE5DDDC),
    outline = Color(0xFF8E8593),
    outlineVariant = Color(0xFFD9D0D6),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

/** Theme flavour the adaptive artwork palette needs to rebuild a matching scheme. */
data class ThemeFlavor(val dark: Boolean, val pureBlack: Boolean, val artworkColors: Boolean)

val LocalThemeFlavor = staticCompositionLocalOf { ThemeFlavor(dark = true, pureBlack = false, artworkColors = false) }

@Composable
fun PlayerTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    artworkColors: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.BLACK -> true
    }
    val context = LocalContext.current
    val base = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    val pureBlack = themeMode == ThemeMode.BLACK
    val colors = if (pureBlack) base.pureBlack() else base
    CompositionLocalProvider(LocalThemeFlavor provides ThemeFlavor(dark, pureBlack, artworkColors)) {
        MaterialTheme(colorScheme = colors, typography = AppTypography, shapes = AppShapes, content = content)
    }
}

internal fun ColorScheme.pureBlack(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0A0A0A),
    surfaceContainer = Color(0xFF111111),
    surfaceContainerHigh = Color(0xFF181818),
    surfaceContainerHighest = Color(0xFF202020),
)
