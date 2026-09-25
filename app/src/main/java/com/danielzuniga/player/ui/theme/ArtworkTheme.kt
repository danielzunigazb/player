package com.danielzuniga.player.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils

/**
 * Re-tints the whole app with the colour of the current album art: accents take the cover's
 * hue and surfaces pick up a faint wash of it. Transitions are animated so changing song
 * feels like the room lighting shifts rather than a hard cut.
 */
@Composable
fun ArtworkTheme(seed: Color?, content: @Composable () -> Unit) {
    val flavor = LocalThemeFlavor.current
    val current = MaterialTheme.colorScheme
    val target = if (flavor.artworkColors && seed != null) {
        schemeFromSeed(seed, current, flavor.dark, flavor.pureBlack)
    } else {
        current
    }
    MaterialTheme(colorScheme = target.animated(), content = content)
}

internal fun schemeFromSeed(seed: Color, base: ColorScheme, dark: Boolean, pureBlack: Boolean): ColorScheme {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(seed.toArgb(), hsl)
    val h = hsl[0]
    // Greyish covers still get a gentle accent instead of a muddy one.
    val s = hsl[1].coerceIn(0.35f, 0.85f)
    fun c(hue: Float, sat: Float, light: Float) =
        Color(ColorUtils.HSLToColor(floatArrayOf((hue + 360f) % 360f, sat.coerceIn(0f, 1f), light.coerceIn(0f, 1f))))

    val second = h + 40f
    val third = h - 60f
    val scheme = if (dark) {
        base.copy(
            primary = c(h, s, 0.74f),
            onPrimary = c(h, s, 0.12f),
            primaryContainer = c(h, s * 0.7f, 0.26f),
            onPrimaryContainer = c(h, s, 0.90f),
            secondary = c(second, s * 0.7f, 0.76f),
            onSecondary = c(second, s, 0.14f),
            secondaryContainer = c(second, s * 0.45f, 0.24f),
            onSecondaryContainer = c(second, s, 0.90f),
            tertiary = c(third, s * 0.8f, 0.74f),
            onTertiary = c(third, s, 0.14f),
            background = c(h, 0.22f, 0.06f),
            surface = c(h, 0.22f, 0.06f),
            surfaceVariant = c(h, 0.14f, 0.20f),
            onSurfaceVariant = c(h, 0.14f, 0.74f),
            surfaceContainerLowest = c(h, 0.20f, 0.04f),
            surfaceContainerLow = c(h, 0.18f, 0.09f),
            surfaceContainer = c(h, 0.17f, 0.11f),
            surfaceContainerHigh = c(h, 0.16f, 0.14f),
            surfaceContainerHighest = c(h, 0.15f, 0.18f),
            outlineVariant = c(h, 0.12f, 0.26f),
        )
    } else {
        base.copy(
            primary = c(h, s, 0.40f),
            onPrimary = Color.White,
            primaryContainer = c(h, s, 0.88f),
            onPrimaryContainer = c(h, s, 0.14f),
            secondary = c(second, s * 0.6f, 0.42f),
            onSecondary = Color.White,
            secondaryContainer = c(second, s * 0.6f, 0.90f),
            onSecondaryContainer = c(second, s, 0.14f),
            tertiary = c(third, s * 0.7f, 0.38f),
            onTertiary = Color.White,
            background = c(h, 0.40f, 0.975f),
            surface = c(h, 0.40f, 0.975f),
            surfaceVariant = c(h, 0.22f, 0.90f),
            onSurfaceVariant = c(h, 0.10f, 0.36f),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = c(h, 0.30f, 0.955f),
            surfaceContainer = c(h, 0.28f, 0.935f),
            surfaceContainerHigh = c(h, 0.26f, 0.915f),
            surfaceContainerHighest = c(h, 0.24f, 0.89f),
            outlineVariant = c(h, 0.18f, 0.84f),
        )
    }
    return if (pureBlack) scheme.pureBlack() else scheme
}

@Composable
private fun ColorScheme.animated(): ColorScheme {
    @Composable
    fun Color.anim(label: String): Color {
        val value by animateColorAsState(this, tween(700), label = label)
        return value
    }
    return copy(
        primary = primary.anim("primary"),
        onPrimary = onPrimary.anim("onPrimary"),
        primaryContainer = primaryContainer.anim("primaryContainer"),
        onPrimaryContainer = onPrimaryContainer.anim("onPrimaryContainer"),
        secondary = secondary.anim("secondary"),
        secondaryContainer = secondaryContainer.anim("secondaryContainer"),
        onSecondaryContainer = onSecondaryContainer.anim("onSecondaryContainer"),
        tertiary = tertiary.anim("tertiary"),
        background = background.anim("background"),
        surface = surface.anim("surface"),
        surfaceVariant = surfaceVariant.anim("surfaceVariant"),
        onSurfaceVariant = onSurfaceVariant.anim("onSurfaceVariant"),
        surfaceContainerLow = surfaceContainerLow.anim("surfaceContainerLow"),
        surfaceContainer = surfaceContainer.anim("surfaceContainer"),
        surfaceContainerHigh = surfaceContainerHigh.anim("surfaceContainerHigh"),
        surfaceContainerHighest = surfaceContainerHighest.anim("surfaceContainerHighest"),
    )
}
