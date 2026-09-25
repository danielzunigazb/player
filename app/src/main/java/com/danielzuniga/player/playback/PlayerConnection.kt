package com.danielzuniga.player.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.danielzuniga.player.data.Song
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class NowPlaying(
    val title: String,
    val artist: String,
    val artworkUri: Uri?,
)

data class PlayerUiState(
    val nowPlaying: NowPlaying? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
)

/** Connects to [PlaybackService] through a MediaController and mirrors its state as a flow. */
class PlayerConnection(context: Context) {

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private val controllerFuture: ListenableFuture<MediaController>

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish(player)
    }

    init {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, token).buildAsync()
        controllerFuture.addListener(
            {
                if (controllerFuture.isCancelled) return@addListener
                val connected = controllerFuture.get()
                connected.addListener(listener)
                controller = connected
                publish(connected)
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    fun play(songs: List<Song>, startIndex: Int, shuffle: Boolean = false) {
        val player = controller ?: return
        if (songs.isEmpty()) return
        player.shuffleModeEnabled = shuffle
        player.setMediaItems(songs.map { it.toMediaItem() }, startIndex, 0L)
        player.prepare()
        player.play()
    }

    fun togglePlayPause() {
        val player = controller ?: return
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
            if (player.playbackState == Player.STATE_ENDED) player.seekToDefaultPosition(0)
            player.play()
        }
    }

    fun next() {
        controller?.seekToNext()
    }

    fun previous() {
        controller?.seekToPrevious()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
        _state.update { it.copy(positionMs = positionMs) }
    }

    fun toggleShuffle() {
        controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }

    fun cycleRepeatMode() {
        val player = controller ?: return
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    /** Player events don't fire as position advances, so the UI polls this while playing. */
    fun refreshPosition() {
        val player = controller ?: return
        if (player.isPlaying) {
            _state.update { it.copy(positionMs = player.currentPosition) }
        }
    }

    fun release() {
        controller?.removeListener(listener)
        controller = null
        MediaController.releaseFuture(controllerFuture)
    }

    private fun publish(player: Player) {
        val metadata = player.mediaMetadata
        val duration = player.duration.takeIf { it != C.TIME_UNSET }
            ?: metadata.durationMs
            ?: 0L
        _state.value = PlayerUiState(
            nowPlaying = player.currentMediaItem?.let {
                NowPlaying(
                    title = metadata.title?.toString().orEmpty(),
                    artist = metadata.artist?.toString().orEmpty(),
                    artworkUri = metadata.artworkUri,
                )
            },
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition,
            durationMs = duration,
            shuffleEnabled = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
        )
    }
}

private fun Song.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(artworkUri)
                .setDurationMs(durationMs)
                .build()
        )
        .build()
