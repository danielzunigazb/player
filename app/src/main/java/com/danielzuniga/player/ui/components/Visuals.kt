package com.danielzuniga.player.ui.components

import android.net.Uri
import android.os.Build
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest

/**
 * Three bouncing bars, the classic "this is what's playing" marker. They settle to a low,
 * still line while paused.
 */
@Composable
fun PlayingBars(playing: Boolean, color: Color, modifier: Modifier = Modifier, size: Dp = 16.dp) {
    val transition = rememberInfiniteTransition(label = "bars")
    val phases = listOf(420, 560, 360).map { duration ->
        transition.animateFloat(
            initialValue = 0.25f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(duration), RepeatMode.Reverse),
            label = "bar",
        )
    }
    val energy by animateFloatAsState(if (playing) 1f else 0f, tween(300), label = "energy")
    Canvas(modifier = modifier.size(size)) {
        val gap = this.size.width / 7f
        val barWidth = gap * 5f / 3f
        phases.forEachIndexed { i, phase ->
            val level = 0.2f + (phase.value - 0.2f) * energy
            val height = this.size.height * level
            drawRoundRect(
                color = color,
                topLeft = Offset(i * (barWidth + gap), this.size.height - height),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(barWidth / 2f),
            )
        }
    }
}

/**
 * Full-bleed, heavily blurred copy of the cover used as a backdrop. Decoding a tiny version and
 * scaling it up already looks soft on older Android; API 31+ adds a real blur on top.
 */
@Composable
fun BlurredArtwork(uri: Uri?, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit = {}) {
    val context = LocalContext.current
    Box(modifier = modifier) {
        if (uri != null) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(uri).size(48).crossfade(600).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.85f)
                    .then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(48.dp) else Modifier),
            )
        }
        content()
    }
}

/** Vertical scrim so text stays readable over the blurred art. */
fun Modifier.scrim(top: Color, bottom: Color): Modifier =
    background(Brush.verticalGradient(0f to top, 0.45f to bottom.copy(alpha = 0.75f), 1f to bottom))
