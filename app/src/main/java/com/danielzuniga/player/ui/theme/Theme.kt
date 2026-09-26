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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.danielzuniga.player.data.ThemeMode

/**
 * Tokens of the "Daniel Zúñiga" design system (claude.ai/design): Obsidiana (dark) and
 * Pergamino (light). Warm neutrals only, gold as *the* accent, verdigris for success/live,
 * ember for errors and small sparks. Hierarchy comes from bg → surface → line, never shadows.
 */
@Immutable
data class DzColors(
    val bg: Color,
    val surface: Color,
    val line: Color,
    val ink: Color,
    val inkMuted: Color,
    val gold: Color,
    val onGold: Color,
    val verdigris: Color,
    val ember: Color,
)

val Obsidiana = DzColors(
    bg = Color(0xFF0E0C0A),
    surface = Color(0xFF181512),
    line = Color(0xFF2F2923),
    ink = Color(0xFFEFE6D6),
    inkMuted = Color(0xFFA2978A),
    gold = Color(0xFFD6A23E),
    onGold = Color(0xFF0E0C0A),
    verdigris = Color(0xFF5CB09C),
    ember = Color(0xFFE0573C),
)

val Pergamino = DzColors(
    bg = Color(0xFFF3EDE2),
    surface = Color(0xFFE9E1D2),
    line = Color(0xFFD3C8B4),
    ink = Color(0xFF1B1713),
    inkMuted = Color(0xFF5C5347),
    gold = Color(0xFF8A5A10),
    onGold = Color(0xFFF3EDE2),
    verdigris = Color(0xFF2B6A5C),
    ember = Color(0xFFB0381F),
)

/** Pure-black variant for AMOLED screens; everything but the page stays on-brand. */
private fun DzColors.amoled() = copy(bg = Color.Black, onGold = Color.Black)

val LocalDzColors = staticCompositionLocalOf { Obsidiana }

/** Shortcut to the brand tokens: `Dz.colors.gold`. */
object Dz {
    val colors: DzColors
        @Composable @ReadOnlyComposable get() = LocalDzColors.current
}

private fun DzColors.toScheme(dark: Boolean): ColorScheme {
    val scheme = if (dark) darkColorScheme() else lightColorScheme()
    return scheme.copy(
        primary = gold,
        onPrimary = onGold,
        primaryContainer = surface,
        onPrimaryContainer = ink,
        secondary = verdigris,
        onSecondary = bg,
        secondaryContainer = surface,
        onSecondaryContainer = ink,
        tertiary = ember,
        onTertiary = bg,
        error = ember,
        onError = bg,
        background = bg,
        onBackground = ink,
        surface = bg,
        onSurface = ink,
        surfaceVariant = surface,
        onSurfaceVariant = inkMuted,
        surfaceTint = Color.Transparent,
        surfaceBright = surface,
        surfaceDim = bg,
        surfaceContainerLowest = bg,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = surface,
        surfaceContainerHighest = line,
        inverseSurface = ink,
        inverseOnSurface = bg,
        inversePrimary = gold,
        outline = inkMuted,
        outlineVariant = line,
        scrim = Color.Black,
    )
}

/** Hard corners: 2dp on controls, none on blocks; nothing is a pill. */
private val DzShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(2.dp),
    medium = RoundedCornerShape(0.dp),
    large = RoundedCornerShape(0.dp),
    extraLarge = RoundedCornerShape(0.dp),
)

@Composable
fun PlayerTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.BLACK -> true
    }
    val tokens = when {
        themeMode == ThemeMode.BLACK -> Obsidiana.amoled()
        dark -> Obsidiana
        else -> Pergamino
    }
    val context = LocalContext.current
    val scheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // Opt-in Material You: keeps the brand's neutrals and shapes, only the accent follows the wallpaper.
        val dynamic = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        tokens.toScheme(dark).copy(primary = dynamic.primary, onPrimary = dynamic.onPrimary)
    } else {
        tokens.toScheme(dark)
    }
    val brand = if (scheme.primary != tokens.gold) tokens.copy(gold = scheme.primary, onGold = scheme.onPrimary) else tokens
    CompositionLocalProvider(LocalDzColors provides brand) {
        MaterialTheme(colorScheme = scheme, typography = DzTypography, shapes = DzShapes, content = content)
    }
}
