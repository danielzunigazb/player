package com.danielzuniga.player.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.danielzuniga.player.R
import com.danielzuniga.player.playback.NowPlaying
import com.danielzuniga.player.playback.PlayerUiState
import com.danielzuniga.player.ui.components.Artwork
import com.danielzuniga.player.ui.components.DzIconButton
import com.danielzuniga.player.ui.theme.Dz
import com.danielzuniga.player.ui.theme.DzIcons
import com.danielzuniga.player.ui.theme.DzType
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun MiniPlayer(
    state: PlayerUiState,
    nowPlaying: NowPlaying,
    onClick: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val swipeThreshold = with(LocalDensity.current) { 72.dp.toPx() }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val c = Dz.colors

    // A docked `surface` panel: its top edge is the progress line (gold over `line`).
    Surface(
        onClick = onClick,
        color = c.surface,
        contentColor = c.ink,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(c.line),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(state.progress())
                        .height(2.dp)
                        .background(c.gold),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            ) {
                // Swipe the song info sideways to change track.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .offset { IntOffset((dragOffset / 2).roundToInt(), 0) }
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    if (abs(dragOffset) > swipeThreshold) {
                                        if (dragOffset < 0) onNext() else onPrevious()
                                    }
                                    dragOffset = 0f
                                },
                                onDragCancel = { dragOffset = 0f },
                                onHorizontalDrag = { _, delta -> dragOffset += delta },
                            )
                        },
                ) {
                    Artwork(uri = nowPlaying.artworkUri, modifier = Modifier.size(44.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = nowPlaying.title,
                            style = DzType.small.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = nowPlaying.artist,
                            style = DzType.small,
                            color = c.inkMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                DzIconButton(
                    icon = if (state.isPlaying) DzIcons.Pause else DzIcons.Play,
                    contentDescription = stringResource(if (state.isPlaying) R.string.pause else R.string.play),
                    onClick = onTogglePlay,
                )
                DzIconButton(
                    icon = DzIcons.Next,
                    contentDescription = stringResource(R.string.next),
                    onClick = onNext,
                    bordered = false,
                )
            }
        }
    }
}

internal fun PlayerUiState.progress(): Float =
    if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
