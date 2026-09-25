package com.danielzuniga.player.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.danielzuniga.player.R
import com.danielzuniga.player.playback.NowPlaying
import com.danielzuniga.player.playback.PlayerUiState
import com.danielzuniga.player.ui.components.Artwork
import com.danielzuniga.player.ui.components.MenuItem
import com.danielzuniga.player.ui.components.rememberArtworkColor
import com.danielzuniga.player.ui.formatDuration
import kotlin.math.abs
import kotlin.math.roundToInt

class NowPlayingActions(
    val onClose: () -> Unit,
    val onTogglePlay: () -> Unit,
    val onNext: () -> Unit,
    val onPrevious: () -> Unit,
    val onSeek: (Long) -> Unit,
    val onToggleShuffle: () -> Unit,
    val onCycleRepeat: () -> Unit,
    val onToggleFavorite: () -> Unit,
    val onOpenQueue: () -> Unit,
    val onOpenSleepTimer: () -> Unit,
    val onOpenSpeed: () -> Unit,
    val onOpenEqualizer: () -> Unit,
    val onAddToPlaylist: () -> Unit,
    val onGoToAlbum: () -> Unit,
    val onGoToArtist: () -> Unit,
)

@Composable
fun NowPlayingScreen(
    state: PlayerUiState,
    nowPlaying: NowPlaying,
    isFavorite: Boolean,
    actions: NowPlayingActions,
) {
    val surface = MaterialTheme.colorScheme.surface
    val artColor = rememberArtworkColor(nowPlaying.artworkUri)
    val topColor by animateColorAsState(
        targetValue = artColor?.copy(alpha = 0.55f) ?: surface,
        animationSpec = tween(600),
        label = "artColor",
    )

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .background(Brush.verticalGradient(listOf(topColor, surface)))
                .safeDrawingPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            TopRow(actions)

            Spacer(Modifier.weight(1f))
            SwipeableArtwork(nowPlaying, onNext = actions.onNext, onPrevious = actions.onPrevious)
            Spacer(Modifier.weight(1f))

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = nowPlaying.title,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = nowPlaying.artist,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = actions.onToggleFavorite) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = stringResource(if (isFavorite) R.string.remove_favorite else R.string.add_favorite),
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            SeekBar(state = state, onSeek = actions.onSeek)
            Spacer(Modifier.height(8.dp))
            TransportControls(state, actions)
            Spacer(Modifier.height(16.dp))
            ExtrasRow(state, actions)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun TopRow(actions: NowPlayingActions) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        IconButton(onClick = actions.onClose) {
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = stringResource(R.string.close_now_playing))
        }
        Text(
            text = stringResource(R.string.now_playing),
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.more_options))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                MenuItem(R.string.add_to_playlist, Icons.AutoMirrored.Rounded.PlaylistAdd) { menuOpen = false; actions.onAddToPlaylist() }
                MenuItem(R.string.go_to_album, Icons.Rounded.Album) { menuOpen = false; actions.onGoToAlbum() }
                MenuItem(R.string.go_to_artist, Icons.Rounded.Person) { menuOpen = false; actions.onGoToArtist() }
                MenuItem(R.string.equalizer, Icons.Rounded.Equalizer) { menuOpen = false; actions.onOpenEqualizer() }
            }
        }
    }
}

@Composable
private fun SwipeableArtwork(nowPlaying: NowPlaying, onNext: () -> Unit, onPrevious: () -> Unit) {
    val threshold = with(LocalDensity.current) { 96.dp.toPx() }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    Artwork(
        uri = nowPlaying.artworkUri,
        cornerRadius = 16.dp,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .offset { IntOffset((dragOffset / 3).roundToInt(), 0) }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (abs(dragOffset) > threshold) {
                            if (dragOffset < 0) onNext() else onPrevious()
                        }
                        dragOffset = 0f
                    },
                    onDragCancel = { dragOffset = 0f },
                    onHorizontalDrag = { _, delta -> dragOffset += delta },
                )
            },
    )
}

@Composable
private fun TransportControls(state: PlayerUiState, actions: NowPlayingActions) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
        IconButton(onClick = actions.onToggleShuffle) {
            Icon(
                Icons.Rounded.Shuffle,
                contentDescription = stringResource(R.string.shuffle),
                tint = activeTint(state.shuffleEnabled),
            )
        }
        IconButton(onClick = actions.onPrevious, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Rounded.SkipPrevious, contentDescription = stringResource(R.string.previous), modifier = Modifier.size(36.dp))
        }
        FilledIconButton(onClick = actions.onTogglePlay, modifier = Modifier.size(72.dp)) {
            Icon(
                imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = stringResource(if (state.isPlaying) R.string.pause else R.string.play),
                modifier = Modifier.size(40.dp),
            )
        }
        IconButton(onClick = actions.onNext, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Rounded.SkipNext, contentDescription = stringResource(R.string.next), modifier = Modifier.size(36.dp))
        }
        IconButton(onClick = actions.onCycleRepeat) {
            Icon(
                imageVector = if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                contentDescription = stringResource(R.string.repeat),
                tint = activeTint(state.repeatMode != Player.REPEAT_MODE_OFF),
            )
        }
    }
}

@Composable
private fun ExtrasRow(state: PlayerUiState, actions: NowPlayingActions) {
    val sleepActive = state.sleepAtMs > 0 || state.sleepAtEndOfTrack
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier.fillMaxWidth(),
    ) {
        TextButton(onClick = actions.onOpenSpeed) {
            Text(
                text = formatSpeed(state.playbackSpeed),
                color = activeTint(state.playbackSpeed != 1f),
                style = MaterialTheme.typography.titleSmall,
            )
        }
        IconButton(onClick = actions.onOpenSleepTimer) {
            Icon(Icons.Rounded.Bedtime, contentDescription = stringResource(R.string.sleep_timer), tint = activeTint(sleepActive))
        }
        IconButton(onClick = actions.onOpenEqualizer) {
            Icon(Icons.Rounded.Equalizer, contentDescription = stringResource(R.string.equalizer), tint = activeTint(false))
        }
        IconButton(onClick = actions.onOpenQueue) {
            Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = stringResource(R.string.queue), tint = activeTint(false))
        }
    }
}

@Composable
private fun SeekBar(state: PlayerUiState, onSeek: (Long) -> Unit) {
    // While dragging, show the thumb where the finger is instead of the live position.
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val fraction = dragFraction ?: state.progress()

    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = fraction,
            onValueChange = { dragFraction = it },
            onValueChangeFinished = {
                dragFraction?.let { onSeek((it * state.durationMs).toLong()) }
                dragFraction = null
            },
            enabled = state.durationMs > 0,
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = formatDuration((fraction * state.durationMs).toLong()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = formatDuration(state.durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun activeTint(active: Boolean): Color =
    if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
