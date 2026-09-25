package com.danielzuniga.player.ui.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Seek bar whose played part is a travelling sine wave while music plays and flattens into a
 * line when paused. Tap or drag anywhere to scrub; [onScrub] reports the finger position and
 * [onScrubEnd] commits it.
 */
@Composable
fun WavySeekBar(
    fraction: Float,
    playing: Boolean,
    color: Color,
    trackColor: Color,
    onScrub: (Float) -> Unit,
    onScrubEnd: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val amplitude by animateDpAsState(if (playing) 3.5.dp else 0.dp, tween(500), label = "amplitude")
    val thumbHeight by animateDpAsState(if (playing) 20.dp else 14.dp, tween(300), label = "thumb")
    val phase by rememberInfiniteTransition(label = "wave").animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "phase",
    )
    val latestScrub by rememberUpdatedState(onScrub)
    val latestEnd by rememberUpdatedState(onScrubEnd)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                if (enabled) {
                    setProgress { value ->
                        latestScrub(value.coerceIn(0f, 1f))
                        latestEnd()
                        true
                    }
                }
            }
            .then(
                if (!enabled) {
                    Modifier
                } else {
                    Modifier
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                latestScrub((offset.x / size.width).coerceIn(0f, 1f))
                                latestEnd()
                            }
                        }
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragStart = { latestScrub((it.x / size.width).coerceIn(0f, 1f)) },
                                onDragEnd = { latestEnd() },
                                onDragCancel = { latestEnd() },
                                onHorizontalDrag = { change, _ ->
                                    latestScrub((change.position.x / size.width).coerceIn(0f, 1f))
                                },
                            )
                        }
                },
            ),
    ) {
        val centerY = size.height / 2f
        val stroke = 4.dp.toPx()
        val splitX = size.width * fraction.coerceIn(0f, 1f)
        val amp = amplitude.toPx()
        val wavelength = 28.dp.toPx()

        // Remaining part: plain, dim line.
        if (splitX < size.width) {
            drawLine(
                color = trackColor,
                start = Offset(splitX + stroke, centerY),
                end = Offset(size.width, centerY),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
        // Played part: the wave.
        if (splitX > 0f) {
            val path = Path()
            var x = 0f
            path.moveTo(0f, centerY)
            while (x <= splitX) {
                val y = centerY + amp * sin(2 * PI * x / wavelength - phase).toFloat()
                path.lineTo(x, y)
                x += 2f
            }
            drawPath(path, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
        }
        // Thumb: a vertical pill.
        val thumbWidth = 6.dp.toPx()
        val th = thumbHeight.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(splitX - thumbWidth / 2f, centerY - th / 2f),
            size = Size(thumbWidth, th),
            cornerRadius = CornerRadius(thumbWidth / 2f),
        )
    }
}
