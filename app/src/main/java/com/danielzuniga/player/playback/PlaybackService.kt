package com.danielzuniga.player.playback

import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.core.os.bundleOf
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.SessionError
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.danielzuniga.player.MainActivity
import com.danielzuniga.player.appContainer
import com.danielzuniga.player.widget.NowPlayingWidget
import com.danielzuniga.player.widget.WidgetState
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns the ExoPlayer so playback survives the UI. Media3 posts the media notification and
 * routes lock-screen / headset / Bluetooth controls through the MediaSession. On top of that
 * this service persists the queue, counts plays, runs the sleep timer and hosts audio effects.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {

    private val container by lazy { appContainer }
    private val scope = MainScope()
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var player: ExoPlayer
    private var mediaSession: MediaLibrarySession? = null
    private val browser by lazy { LibraryBrowser(this, container) }

    private var currentPlayCounted = false
    private var sleepAtMs = 0L
    private var sleepAtEndOfTrack = false
    private val sleepRunnable = Runnable {
        player.pause()
        setSleepTimer(SessionCommands.SLEEP_OFF)
    }
    private val periodicSave = object : Runnable {
        override fun run() {
            if (player.isPlaying) saveQueue()
            handler.postDelayed(this, SAVE_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player.addListener(PlayerEvents())
        container.audioEffects.attach(player.audioSessionId)

        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        mediaSession = MediaLibrarySession.Builder(this, player, SessionCallback())
            .setSessionActivity(openAppIntent)
            .build()

        restoreQueue()
        handler.postDelayed(periodicSave, SAVE_INTERVAL_MS)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        saveQueue()
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        saveQueue()
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
        container.audioEffects.release()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    private fun restoreQueue() {
        scope.launch {
            val restored = loadSavedItems() ?: return@launch
            // The user may have started something while the library was loading.
            if (player.mediaItemCount > 0) return@launch
            player.shuffleModeEnabled = restored.shuffle
            player.repeatMode = restored.repeatMode
            player.setMediaItems(restored.items.mediaItems, restored.items.startIndex, restored.items.startPositionMs)
            // Left unprepared on purpose: no notification until the user presses play.
        }
    }

    private class RestoredQueue(
        val items: MediaItemsWithStartPosition,
        val shuffle: Boolean,
        val repeatMode: Int,
    )

    private suspend fun loadSavedItems(): RestoredQueue? {
        val saved = container.playbackState.load() ?: return null
        val library = container.musicRepository.awaitLibrary()
        val savedCurrentId = saved.songIds.getOrNull(saved.index)
        val songs = library.songs(saved.songIds)
        if (songs.isEmpty()) return null
        val index = songs.indexOfFirst { it.id == savedCurrentId }
        return RestoredQueue(
            items = MediaItemsWithStartPosition(
                songs.map { it.toMediaItem() },
                index.coerceAtLeast(0),
                if (index >= 0) saved.positionMs else 0L,
            ),
            shuffle = saved.shuffle,
            repeatMode = saved.repeatMode,
        )
    }

    private fun saveQueue() {
        if (!::player.isInitialized || player.mediaItemCount == 0) return
        container.playbackState.save(
            SavedQueue(
                songIds = (0 until player.mediaItemCount).mapNotNull {
                    player.getMediaItemAt(it).mediaId.toLongOrNull()
                },
                index = player.currentMediaItemIndex,
                positionMs = player.currentPosition,
                shuffle = player.shuffleModeEnabled,
                repeatMode = player.repeatMode,
            )
        )
    }

    private var widgetArtUri: Uri? = null
    private var widgetArt: Bitmap? = null

    private fun updateWidget() {
        if (!NowPlayingWidget.hasWidgets(this)) return
        val item = player.currentMediaItem
        val metadata = item?.mediaMetadata
        val state = WidgetState(
            title = metadata?.title?.toString(),
            artist = metadata?.artist?.toString(),
            isPlaying = player.isPlaying,
            artworkUri = metadata?.artworkUri,
        )
        NowPlayingWidget.saveState(this, state)
        scope.launch {
            // Only decode the cover again when the album changes.
            if (state.artworkUri != widgetArtUri) {
                widgetArt = withContext(Dispatchers.IO) { NowPlayingWidget.loadArtwork(this@PlaybackService, state.artworkUri) }
                widgetArtUri = state.artworkUri
            }
            NowPlayingWidget.push(this@PlaybackService, state, widgetArt)
        }
    }

    private fun setSleepTimer(minutes: Int) {
        handler.removeCallbacks(sleepRunnable)
        player.pauseAtEndOfMediaItems = false
        sleepAtMs = 0L
        sleepAtEndOfTrack = false
        when {
            minutes > 0 -> {
                val delayMs = minutes * 60_000L
                sleepAtMs = System.currentTimeMillis() + delayMs
                handler.postDelayed(sleepRunnable, delayMs)
            }
            minutes == SessionCommands.SLEEP_END_OF_TRACK -> {
                sleepAtEndOfTrack = true
                player.pauseAtEndOfMediaItems = true
            }
        }
        mediaSession?.setSessionExtras(
            bundleOf(
                SessionCommands.EXTRA_SLEEP_AT to sleepAtMs,
                SessionCommands.EXTRA_SLEEP_END_OF_TRACK to sleepAtEndOfTrack,
            )
        )
    }

    private inner class PlayerEvents : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) currentPlayCounted = false
            if (player.isPlaying && !currentPlayCounted) {
                currentPlayCounted = true
                player.currentMediaItem?.mediaId?.toLongOrNull()?.let { id ->
                    scope.launch { container.userData.recordPlay(id) }
                }
            }
            if (events.containsAny(
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                    Player.EVENT_REPEAT_MODE_CHANGED,
                )
            ) {
                saveQueue()
            }
            if (events.containsAny(
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_IS_PLAYING_CHANGED,
                    Player.EVENT_MEDIA_METADATA_CHANGED,
                )
            ) {
                updateWidget()
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady) saveQueue()
            if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM && sleepAtEndOfTrack) {
                setSleepTimer(SessionCommands.SLEEP_OFF)
            }
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            container.audioEffects.attach(audioSessionId)
        }
    }

    private inner class SessionCallback : MediaLibrarySession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                .add(SessionCommand(SessionCommands.SLEEP_TIMER, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == SessionCommands.SLEEP_TIMER) {
                setSleepTimer(args.getInt(SessionCommands.ARG_MINUTES, SessionCommands.SLEEP_OFF))
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }

        // Items from controllers or browsers can arrive with only an id; fill in URI and metadata.
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> =
            Futures.immediateFuture(mediaItems.mapTo(mutableListOf()) { browser.resolveItem(it) })

        // A song picked in Android Auto queues the rest of its album or playlist too.
        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaItemsWithStartPosition> = future {
            val (items, index) = browser.resolveQueue(mediaItems, startIndex)
            val position = if (index == startIndex) startPositionMs else C.TIME_UNSET
            MediaItemsWithStartPosition(items, index, position)
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(this@PlaybackService.browser.root(), params))

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = future {
            val children = this@PlaybackService.browser.children(parentId)
            if (children == null) {
                LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
            } else {
                LibraryResult.ofItemList(children.page(page, pageSize), params)
            }
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> = future {
            this@PlaybackService.browser.item(mediaId)
                ?.let { LibraryResult.ofItem(it, null) }
                ?: LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<Void>> = future {
            val count = this@PlaybackService.browser.search(query).size
            session.notifySearchResultChanged(browser, query, count, params)
            LibraryResult.ofVoid()
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = future {
            LibraryResult.ofItemList(this@PlaybackService.browser.search(query).page(page, pageSize), params)
        }

        // Lets the system media controls (Android 13+) resume the last queue after a reboot.
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaItemsWithStartPosition> = future {
            val restored = loadSavedItems() ?: throw UnsupportedOperationException("Nothing to resume")
            player.shuffleModeEnabled = restored.shuffle
            player.repeatMode = restored.repeatMode
            restored.items
        }
    }

    /** Runs [block] on the main scope and exposes it as the future Media3 callbacks expect. */
    private fun <T> future(block: suspend () -> T): ListenableFuture<T> {
        val result = SettableFuture.create<T>()
        scope.launch {
            try {
                result.set(block())
            } catch (e: Exception) {
                result.setException(e)
            }
        }
        return result
    }

    private companion object {
        const val SAVE_INTERVAL_MS = 15_000L

        fun <T> List<T>.page(page: Int, pageSize: Int): ImmutableList<T> {
            if (pageSize <= 0 || pageSize == Int.MAX_VALUE) return ImmutableList.copyOf(this)
            val from = (page.toLong() * pageSize).coerceAtMost(size.toLong()).toInt()
            return ImmutableList.copyOf(subList(from, minOf(from + pageSize, size)))
        }
    }
}
