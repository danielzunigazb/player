package com.danielzuniga.player.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.danielzuniga.player.data.Song
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class NowPlaying(
    val songId: Long?,
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
    val playbackSpeed: Float = 1f,
    val sleepAtMs: Long = 0L,
    val sleepAtEndOfTrack: Boolean = false,
)

/** One queue entry; [index] is its position in the player, which may differ from display order when shuffling. */
data class QueueItem(
    val index: Int,
    val songId: Long?,
    val title: String,
    val artist: String,
    val artworkUri: Uri?,
)

data class QueueState(
    val items: List<QueueItem> = emptyList(),
    val currentIndex: Int = C.INDEX_UNSET,
    val shuffled: Boolean = false,
)

/** Connects to [PlaybackService] through a MediaController and mirrors its state as flows. */
class PlayerConnection(context: Context) {

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val _queue = MutableStateFlow(QueueState())
    val queue: StateFlow<QueueState> = _queue.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private var controller: MediaController? = null
    private val controllerFuture: ListenableFuture<MediaController>

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            publish(player)
            if (events.containsAny(
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                )
            ) {
                publishQueue(player)
            }
        }
    }

    private val controllerListener = object : MediaController.Listener {
        override fun onExtrasChanged(controller: MediaController, extras: Bundle) = publishExtras(extras)
    }

    init {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, token)
            .setListener(controllerListener)
            .buildAsync()
        controllerFuture.addListener(
            {
                if (controllerFuture.isCancelled) return@addListener
                val connected = controllerFuture.get()
                connected.addListener(playerListener)
                controller = connected
                publish(connected)
                publishQueue(connected)
                publishExtras(connected.sessionExtras)
                _isConnected.value = true
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    fun play(songs: List<Song>, startIndex: Int, shuffle: Boolean = false) {
        val player = controller ?: return
        if (songs.isEmpty()) return
        player.shuffleModeEnabled = shuffle
        player.setMediaItems(songs.map { it.toMediaItem() }, startIndex.coerceIn(songs.indices), 0L)
        player.prepare()
        player.play()
    }

    fun playNext(songs: List<Song>) {
        val player = controller ?: return
        if (player.mediaItemCount == 0) return play(songs, 0)
        player.addMediaItems(player.currentMediaItemIndex + 1, songs.map { it.toMediaItem() })
    }

    fun addToQueue(songs: List<Song>) {
        val player = controller ?: return
        if (player.mediaItemCount == 0) return play(songs, 0)
        player.addMediaItems(songs.map { it.toMediaItem() })
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

    fun skipToQueueItem(index: Int) {
        val player = controller ?: return
        player.seekToDefaultPosition(index)
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
        player.play()
    }

    fun removeQueueItem(index: Int) {
        controller?.removeMediaItem(index)
    }

    fun moveQueueItem(from: Int, to: Int) {
        controller?.moveMediaItem(from, to)
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

    fun setPlaybackSpeed(speed: Float) {
        controller?.setPlaybackSpeed(speed)
    }

    fun setSleepTimer(minutes: Int) {
        controller?.sendCustomCommand(
            SessionCommand(SessionCommands.SLEEP_TIMER, Bundle.EMPTY),
            bundleOf(SessionCommands.ARG_MINUTES to minutes),
        )
    }

    /** Player events don't fire as position advances, so the UI polls this while playing. */
    fun refreshPosition() {
        val player = controller ?: return
        if (player.isPlaying) {
            _state.update { it.copy(positionMs = player.currentPosition) }
        }
    }

    fun release() {
        controller?.removeListener(playerListener)
        controller = null
        _isConnected.value = false
        MediaController.releaseFuture(controllerFuture)
    }

    private fun publish(player: Player) {
        // The player-level metadata can lag behind a queue change; the item's own is always set.
        val metadata = player.mediaMetadata
        val itemMetadata = player.currentMediaItem?.mediaMetadata
        val duration = player.duration.takeIf { it != C.TIME_UNSET }
            ?: metadata.durationMs
            ?: itemMetadata?.durationMs
            ?: 0L
        _state.update { current ->
            current.copy(
                nowPlaying = player.currentMediaItem?.let {
                    NowPlaying(
                        songId = it.mediaId.toLongOrNull(),
                        title = (metadata.title ?: itemMetadata?.title)?.toString().orEmpty(),
                        artist = (metadata.artist ?: itemMetadata?.artist)?.toString().orEmpty(),
                        artworkUri = metadata.artworkUri ?: itemMetadata?.artworkUri,
                    )
                },
                isPlaying = player.isPlaying,
                positionMs = player.currentPosition,
                durationMs = duration,
                shuffleEnabled = player.shuffleModeEnabled,
                repeatMode = player.repeatMode,
                playbackSpeed = player.playbackParameters.speed,
            )
        }
    }

    private fun publishQueue(player: Player) {
        val timeline = player.currentTimeline
        val shuffled = player.shuffleModeEnabled
        _queue.value = QueueState(
            items = playbackOrder(timeline, shuffled).map { index ->
                player.getMediaItemAt(index).toQueueItem(index)
            },
            currentIndex = player.currentMediaItemIndex,
            shuffled = shuffled,
        )
    }

    private fun publishExtras(extras: Bundle) {
        _state.update {
            it.copy(
                sleepAtMs = extras.getLong(SessionCommands.EXTRA_SLEEP_AT, 0L),
                sleepAtEndOfTrack = extras.getBoolean(SessionCommands.EXTRA_SLEEP_END_OF_TRACK, false),
            )
        }
    }

    private fun playbackOrder(timeline: Timeline, shuffled: Boolean): List<Int> {
        if (timeline.isEmpty) return emptyList()
        val order = ArrayList<Int>(timeline.windowCount)
        var index = timeline.getFirstWindowIndex(shuffled)
        while (index != C.INDEX_UNSET) {
            order += index
            index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, shuffled)
        }
        return order
    }

    private fun MediaItem.toQueueItem(index: Int) = QueueItem(
        index = index,
        songId = mediaId.toLongOrNull(),
        title = mediaMetadata.title?.toString().orEmpty(),
        artist = mediaMetadata.artist?.toString().orEmpty(),
        artworkUri = mediaMetadata.artworkUri,
    )
}
