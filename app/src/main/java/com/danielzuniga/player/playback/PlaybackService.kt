package com.danielzuniga.player.playback

import android.app.PendingIntent
import android.content.Intent
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
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.danielzuniga.player.MainActivity
import com.danielzuniga.player.appContainer
import com.danielzuniga.player.data.songUri
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Owns the ExoPlayer so playback survives the UI. Media3 posts the media notification and
 * routes lock-screen / headset / Bluetooth controls through the MediaSession. On top of that
 * this service persists the queue, counts plays, runs the sleep timer and hosts audio effects.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private val container by lazy { appContainer }
    private val scope = MainScope()
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var player: ExoPlayer
    private var mediaSession: MediaSession? = null

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
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(openAppIntent)
            .setCallback(SessionCallback())
            .build()

        restoreQueue()
        handler.postDelayed(periodicSave, SAVE_INTERVAL_MS)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
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

    private inner class SessionCallback : MediaSession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
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

        // Media items reaching the session from a controller may arrive without their URI,
        // so rebuild it from the MediaStore id carried in mediaId.
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            val resolved = mediaItems.mapTo(mutableListOf()) { item ->
                if (item.localConfiguration != null) {
                    item
                } else {
                    item.buildUpon().setUri(songUri(item.mediaId.toLong())).build()
                }
            }
            return Futures.immediateFuture(resolved)
        }

        // Lets the system media controls (Android 13+) resume the last queue after a reboot.
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaItemsWithStartPosition> {
            val future = SettableFuture.create<MediaItemsWithStartPosition>()
            scope.launch {
                val restored = loadSavedItems()
                if (restored == null) {
                    future.setException(UnsupportedOperationException("Nothing to resume"))
                } else {
                    player.shuffleModeEnabled = restored.shuffle
                    player.repeatMode = restored.repeatMode
                    future.set(restored.items)
                }
            }
            return future
        }
    }

    private companion object {
        const val SAVE_INTERVAL_MS = 15_000L
    }
}
