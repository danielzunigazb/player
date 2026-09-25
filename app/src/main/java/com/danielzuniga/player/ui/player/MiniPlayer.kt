package com.danielzuniga.player.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.danielzuniga.player.R
import com.danielzuniga.player.playback.NowPlaying
import com.danielzuniga.player.playback.PlayerUiState
import com.danielzuniga.player.ui.components.Artwork
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
    val colors = MaterialTheme.colorScheme

    // Floating card: lists scroll underneath it, so it reads as a layer above the library.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Surface(
            onClick = onClick,
            shape = MaterialTheme.shapes.large,
            color = colors.surfaceContainerHigh,
            shadowElevation = 12.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier.background(
                    Brush.horizontalGradient(
                        listOf(colors.primaryContainer.copy(alpha = 0.85f), colors.surfaceContainerHigh),
                    ),
                ),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
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
                        Artwork(uri = nowPlaying.artworkUri, cornerRadius = 14.dp, modifier = Modifier.size(48.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = nowPlaying.title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = nowPlaying.artist,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    // Play/pause wrapped in a progress ring.
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(48.dp)) {
                        CircularProgressIndicator(
                            progress = { state.progress() },
                            color = colors.primary,
                            trackColor = colors.onSurface.copy(alpha = 0.12f),
                            strokeWidth = 3.dp,
                            gapSize = 0.dp,
                            modifier = Modifier.fillMaxSize(),
                        )
                        IconButton(onClick = onTogglePlay) {
                            Icon(
                                imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = stringResource(if (state.isPlaying) R.string.pause else R.string.play),
                            )
                        }
                    }
                    IconButton(onClick = onNext) {
                        Icon(Icons.Rounded.SkipNext, contentDescription = stringResource(R.string.next))
                    }
                }
            }
        }
    }
}

internal fun PlayerUiState.progress(): Float =
    if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
