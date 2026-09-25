package com.danielzuniga.player.ui.player

import androidx.compose.animation.core.tween
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import com.danielzuniga.player.ui.components.BlurredArtwork
import com.danielzuniga.player.ui.components.scrim
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
import androidx.compose.material.icons.rounded.Lyrics
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.danielzuniga.player.R
import com.danielzuniga.player.playback.NowPlaying
import com.danielzuniga.player.playback.PlayerUiState
import com.danielzuniga.player.ui.components.Artwork
import com.danielzuniga.player.ui.components.MenuItem
import com.danielzuniga.player.ui.LyricsUiState
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
    lyrics: LyricsUiState,
    actions: NowPlayingActions,
) {
    var showLyrics by rememberSaveable { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme

    Surface(color = colors.surface, modifier = Modifier.fillMaxSize()) {
        BlurredArtwork(uri = nowPlaying.artworkUri, modifier = Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .scrim(top = colors.surface.copy(alpha = 0.25f), bottom = colors.surface),
            )
            // Coloured glow behind the cover; carries the mood even where blur isn't available.
            val glow = colors.primary.copy(alpha = 0.35f)
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRect(
                            Brush.radialGradient(
                                colors = listOf(glow, Color.Transparent),
                                center = Offset(size.width / 2f, size.height * 0.35f),
                                radius = size.width * 0.95f,
                            ),
                        )
                    },
            )
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
                        .padding(vertical = 20.dp),
                ) {
                    if (showLyrics) {
                        LyricsView(lyrics, state.positionMs, actions.onSeek)
                    } else {
                        SwipeableArtwork(
                            nowPlaying = nowPlaying,
                            playing = state.isPlaying,
                            onNext = actions.onNext,
                            onPrevious = actions.onPrevious,
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = nowPlaying.title,
                            style = MaterialTheme.typography.headlineMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = nowPlaying.artist,
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    FavoriteButton(isFavorite, actions.onToggleFavorite)
                }

                Spacer(Modifier.height(12.dp))
                SeekBar(state = state, onSeek = actions.onSeek)
                Spacer(Modifier.height(12.dp))
                TransportControls(state, actions)
                Spacer(Modifier.height(20.dp))
                ExtrasRow(state, actions, showLyrics = showLyrics, onToggleLyrics = { showLyrics = !showLyrics })
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun TopRow(actions: NowPlayingActions) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        GlassIconButton(onClick = actions.onClose) {
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = stringResource(R.string.close_now_playing))
        }
        Text(
            text = stringResource(R.string.now_playing),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        Box {
            GlassIconButton(onClick = { menuOpen = true }) {
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

/** Round, translucent button that sits on top of the blurred backdrop. */
@Composable
private fun GlassIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.size(44.dp),
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
private fun FavoriteButton(isFavorite: Boolean, onToggle: () -> Unit) {
    // A little "pop" whenever the heart fills.
    val scale = remember { Animatable(1f) }
    LaunchedEffect(isFavorite) {
        if (isFavorite) {
            scale.snapTo(0.6f)
            scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMediumLow))
        }
    }
    IconButton(onClick = onToggle) {
        Icon(
            imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            contentDescription = stringResource(if (isFavorite) R.string.remove_favorite else R.string.add_favorite),
            tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(28.dp)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                },
        )
    }
}

@Composable
private fun SwipeableArtwork(nowPlaying: NowPlaying, playing: Boolean, onNext: () -> Unit, onPrevious: () -> Unit) {
    val threshold = with(LocalDensity.current) { 96.dp.toPx() }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    // The cover "breathes out" while playing and steps back when paused.
    val scale by animateFloatAsState(
        targetValue = if (playing) 1f else 0.86f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "artScale",
    )
    val glow = MaterialTheme.colorScheme.primary
    AnimatedContent(
        targetState = nowPlaying.songId to nowPlaying.artworkUri,
        transitionSpec = {
            (fadeIn(tween(350)) + scaleIn(tween(350), initialScale = 0.92f))
                .togetherWith(fadeOut(tween(250)))
        },
        contentAlignment = Alignment.Center,
        label = "artwork",
    ) { (_, uri) ->
        Artwork(
            uri = uri,
            cornerRadius = 28.dp,
            modifier = Modifier
                .aspectRatio(1f)
                .offset { IntOffset((dragOffset / 3).roundToInt(), 0) }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    rotationZ = dragOffset / 60f
                    shadowElevation = 32.dp.toPx() * scale
                    shape = RoundedCornerShape(28.dp)
                    clip = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        ambientShadowColor = glow
                        spotShadowColor = glow
                    }
                }
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
            Icon(
                Icons.Rounded.Shuffle,
                contentDescription = stringResource(R.string.shuffle),
                tint = activeTint(state.shuffleEnabled),
            )
        }
        IconButton(onClick = actions.onPrevious, modifier = Modifier.size(60.dp)) {
            Icon(Icons.Rounded.SkipPrevious, contentDescription = stringResource(R.string.previous), modifier = Modifier.size(40.dp))
        }
        MorphingPlayButton(playing = state.isPlaying, onClick = actions.onTogglePlay)
        IconButton(onClick = actions.onNext, modifier = Modifier.size(60.dp)) {
            Icon(Icons.Rounded.SkipNext, contentDescription = stringResource(R.string.next), modifier = Modifier.size(40.dp))
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

/** Circle while paused, squircle while playing: the shape itself tells the state. */
@Composable
private fun MorphingPlayButton(playing: Boolean, onClick: () -> Unit) {
    val corner by animateIntAsState(
        targetValue = if (playing) 30 else 50,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "corner",
    )
    val width by animateDpAsState(
        targetValue = if (playing) 96.dp else 80.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "width",
    )
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(percent = corner),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shadowElevation = 8.dp,
        modifier = Modifier.size(width = width, height = 80.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = stringResource(if (playing) R.string.pause else R.string.play),
                modifier = Modifier.size(40.dp),
            )
        }
    }
}

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
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        TextButton(onClick = actions.onOpenSpeed) {
            Text(
                text = formatSpeed(state.playbackSpeed),
                color = activeTint(state.playbackSpeed != 1f),
                style = MaterialTheme.typography.labelLarge,
            )
        }
        IconButton(onClick = actions.onOpenSleepTimer) {
            Icon(Icons.Rounded.Bedtime, contentDescription = stringResource(R.string.sleep_timer), tint = activeTint(sleepActive))
        }
        IconButton(onClick = onToggleLyrics) {
            Icon(Icons.Rounded.Lyrics, contentDescription = stringResource(R.string.lyrics), tint = activeTint(showLyrics))
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
        WavySeekBar(
            fraction = fraction,
            playing = state.isPlaying && dragFraction == null,
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
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
