package com.danielzuniga.player.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Three square-cut level bars marking the song that's loaded. They move only while audio is
 * playing and sit as a flat low line when paused.
 */
@Composable
fun PlayingBars(playing: Boolean, color: Color, modifier: Modifier = Modifier, size: Dp = 14.dp) {
    val transition = rememberInfiniteTransition(label = "bars")
    val phases = listOf(420, 560, 360).map { duration ->
        transition.animateFloat(
            initialValue = 0.25f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(duration), RepeatMode.Reverse),
            label = "bar",
        )
    }
    Canvas(modifier = modifier.size(size)) {
        val gap = this.size.width / 7f
        val barWidth = gap * 5f / 3f
        phases.forEachIndexed { i, phase ->
            val level = if (playing) phase.value else 0.25f
            val height = this.size.height * level
            drawRect(
                color = color,
                topLeft = Offset(i * (barWidth + gap), this.size.height - height),
                size = Size(barWidth, height),
            )
        }
    }
}
