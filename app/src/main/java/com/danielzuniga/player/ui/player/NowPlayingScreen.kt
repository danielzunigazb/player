package com.danielzuniga.player.ui.player

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.danielzuniga.player.R
import com.danielzuniga.player.data.TagText
import com.danielzuniga.player.playback.NowPlaying
import com.danielzuniga.player.playback.PlayerUiState
import com.danielzuniga.player.ui.LyricsUiState
import com.danielzuniga.player.ui.components.Artwork
import com.danielzuniga.player.ui.components.DzControlShape
import com.danielzuniga.player.ui.components.DzIconButton
import com.danielzuniga.player.ui.components.DzTitle
import com.danielzuniga.player.ui.components.Eyebrow
import com.danielzuniga.player.ui.components.MenuItem
import com.danielzuniga.player.ui.formatDuration
import com.danielzuniga.player.ui.theme.Dz
import com.danielzuniga.player.ui.theme.DzIcons
import com.danielzuniga.player.ui.theme.DzType
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

/**
 * Obsidian page, the cover as a hard-edged block, the title in mono with the artist as the
 * screen's single serif whisper, and one gold primary: play.
 */
@Composable
fun NowPlayingScreen(
    state: PlayerUiState,
    nowPlaying: NowPlaying,
    isFavorite: Boolean,
    lyrics: LyricsUiState,
    actions: NowPlayingActions,
) {
    var showLyrics by rememberSaveable { mutableStateOf(false) }

    Surface(color = Dz.colors.bg, contentColor = Dz.colors.ink, modifier = Modifier.fillMaxSize()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            TopRow(actions)

            // Takes whatever height is left so the controls always fit (small screens, landscape).
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
            ) {
                if (showLyrics) {
                    LyricsView(lyrics, state.positionMs, actions.onSeek)
                } else {
                    SwipeableArtwork(nowPlaying, onNext = actions.onNext, onPrevious = actions.onPrevious)
                }
            }

            Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                DzTitle(
                    text = nowPlaying.title,
                    // The whisper is meant to be a few words: long collab credits get shortened.
                    whisper = TagText.shortCredit(TagText.artists(nowPlaying.artist), keepNamesWhole = true).takeIf { it.isNotBlank() },
                    whisperOnNewLine = true,
                    style = DzType.h1.copy(fontSize = DzType.h2.fontSize * 1.3f, lineHeight = DzType.h2.lineHeight * 1.3f),
                    maxLines = 3,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = actions.onToggleFavorite) {
                    Icon(
                        imageVector = if (isFavorite) DzIcons.HeartFilled else DzIcons.Heart,
                        contentDescription = stringResource(if (isFavorite) R.string.remove_favorite else R.string.add_favorite),
                        tint = if (isFavorite) Dz.colors.gold else Dz.colors.inkMuted,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            SeekBar(state = state, onSeek = actions.onSeek)
            Spacer(Modifier.height(12.dp))
            TransportControls(state, actions)
            Spacer(Modifier.height(24.dp))
            ExtrasRow(state, actions, showLyrics = showLyrics, onToggleLyrics = { showLyrics = !showLyrics })
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun TopRow(actions: NowPlayingActions) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        DzIconButton(DzIcons.ChevronDown, stringResource(R.string.close_now_playing), actions.onClose)
        Eyebrow(
            text = stringResource(R.string.now_playing),
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        Box {
            DzIconButton(DzIcons.More, stringResource(R.string.more_options), { menuOpen = true })
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                MenuItem(R.string.add_to_playlist, DzIcons.PlaylistAdd) { menuOpen = false; actions.onAddToPlaylist() }
                MenuItem(R.string.go_to_album, DzIcons.Album) { menuOpen = false; actions.onGoToAlbum() }
                MenuItem(R.string.go_to_artist, DzIcons.Artist) { menuOpen = false; actions.onGoToArtist() }
                MenuItem(R.string.equalizer, DzIcons.Equalizer) { menuOpen = false; actions.onOpenEqualizer() }
            }
        }
    }
}

@Composable
private fun SwipeableArtwork(nowPlaying: NowPlaying, onNext: () -> Unit, onPrevious: () -> Unit) {
    val threshold = with(LocalDensity.current) { 96.dp.toPx() }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    // Fast and dry: a 180ms crossfade between covers, no bounce.
    Crossfade(targetState = nowPlaying.artworkUri, animationSpec = tween(180), label = "artwork") { uri ->
        Artwork(
            uri = uri,
            modifier = Modifier
                .aspectRatio(1f)
                .offset { IntOffset((dragOffset / 3).roundToInt(), 0) }
                .border(1.dp, Dz.colors.line)
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
}

@Composable
private fun TransportControls(state: PlayerUiState, actions: NowPlayingActions) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
        IconButton(onClick = actions.onToggleShuffle) {
            Icon(DzIcons.Shuffle, contentDescription = stringResource(R.string.shuffle), tint = activeTint(state.shuffleEnabled))
        }
        IconButton(onClick = actions.onPrevious, modifier = Modifier.size(56.dp)) {
            Icon(DzIcons.Previous, contentDescription = stringResource(R.string.previous), modifier = Modifier.size(30.dp))
        }
        Surface(
            onClick = actions.onTogglePlay,
            shape = DzControlShape,
            color = Dz.colors.gold,
            contentColor = Dz.colors.onGold,
            modifier = Modifier.size(76.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (state.isPlaying) DzIcons.Pause else DzIcons.Play,
                    contentDescription = stringResource(if (state.isPlaying) R.string.pause else R.string.play),
                    modifier = Modifier.size(34.dp),
                )
            }
        }
        IconButton(onClick = actions.onNext, modifier = Modifier.size(56.dp)) {
            Icon(DzIcons.Next, contentDescription = stringResource(R.string.next), modifier = Modifier.size(30.dp))
        }
        IconButton(onClick = actions.onCycleRepeat) {
            Icon(
                imageVector = if (state.repeatMode == Player.REPEAT_MODE_ONE) DzIcons.RepeatOne else DzIcons.Repeat,
                contentDescription = stringResource(R.string.repeat),
                tint = activeTint(state.repeatMode != Player.REPEAT_MODE_OFF),
            )
        }
    }
}

/** Secondary tools in one hairline-framed strip; active ones turn gold. */
@Composable
private fun ExtrasRow(
    state: PlayerUiState,
    actions: NowPlayingActions,
    showLyrics: Boolean,
    onToggleLyrics: () -> Unit,
) {
    val sleepActive = state.sleepAtMs > 0 || state.sleepAtEndOfTrack
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Dz.colors.line),
    ) {
        Surface(onClick = actions.onOpenSpeed, color = Color.Transparent, shape = DzControlShape) {
            Text(
                text = formatSpeed(state.playbackSpeed),
                color = activeTint(state.playbackSpeed != 1f),
                style = DzType.small,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            )
        }
        IconButton(onClick = actions.onOpenSleepTimer) {
            Icon(DzIcons.Moon, contentDescription = stringResource(R.string.sleep_timer), tint = activeTint(sleepActive))
        }
        IconButton(onClick = onToggleLyrics) {
            Icon(DzIcons.Lyrics, contentDescription = stringResource(R.string.lyrics), tint = activeTint(showLyrics))
        }
        IconButton(onClick = actions.onOpenEqualizer) {
            Icon(DzIcons.Equalizer, contentDescription = stringResource(R.string.equalizer), tint = activeTint(false))
        }
        IconButton(onClick = actions.onOpenQueue) {
            Icon(DzIcons.Queue, contentDescription = stringResource(R.string.queue), tint = activeTint(false))
        }
    }
}

@Composable
private fun SeekBar(state: PlayerUiState, onSeek: (Long) -> Unit) {
    // While dragging, show the thumb where the finger is instead of the live position.
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val fraction = dragFraction ?: state.progress()

    Column(modifier = Modifier.fillMaxWidth()) {
        TerminalSeekBar(
            fraction = fraction,
            onScrub = { dragFraction = it },
            onScrubEnd = {
                dragFraction?.let { onSeek((it * state.durationMs).toLong()) }
                dragFraction = null
            },
            enabled = state.durationMs > 0,
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = formatDuration((fraction * state.durationMs).toLong()),
                style = DzType.small,
                color = Dz.colors.inkMuted,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = formatDuration(state.durationMs),
                style = DzType.small,
                color = Dz.colors.inkMuted,
            )
        }
    }
}

@Composable
private fun activeTint(active: Boolean): Color = if (active) Dz.colors.gold else Dz.colors.inkMuted
