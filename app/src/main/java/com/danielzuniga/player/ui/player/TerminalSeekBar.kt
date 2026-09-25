package com.danielzuniga.player.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import com.danielzuniga.player.ui.theme.Dz

/**
 * Terminal-precise seek bar: a 2dp `line` track, the played part in gold and the block cursor
 * as the thumb. Tap or drag anywhere; [onScrub] follows the finger and [onScrubEnd] commits.
 */
@Composable
fun TerminalSeekBar(
    fraction: Float,
    onScrub: (Float) -> Unit,
    onScrubEnd: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val gold = Dz.colors.gold
    val line = Dz.colors.line
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
        val track = 2.dp.toPx()
        val centerY = size.height / 2f
        val x = size.width * fraction.coerceIn(0f, 1f)
        drawRect(line, topLeft = Offset(0f, centerY - track / 2f), size = Size(size.width, track))
        drawRect(gold, topLeft = Offset(0f, centerY - track / 2f), size = Size(x, track))
        val cursorW = 8.dp.toPx()
        val cursorH = 16.dp.toPx()
        drawRect(
            gold,
            topLeft = Offset((x - cursorW / 2f).coerceIn(0f, size.width - cursorW), centerY - cursorH / 2f),
            size = Size(cursorW, cursorH),
        )
    }
}
