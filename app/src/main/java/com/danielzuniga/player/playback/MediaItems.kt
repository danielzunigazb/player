package com.danielzuniga.player.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.danielzuniga.player.data.Song

fun Song.toMediaItem(): MediaItem =
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
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .build()
        )
        .build()

object SessionCommands {
    const val SLEEP_TIMER = "com.danielzuniga.player.SLEEP_TIMER"

    /** Minutes until pause; [SLEEP_OFF] cancels, [SLEEP_END_OF_TRACK] waits for the song to end. */
    const val ARG_MINUTES = "minutes"
    const val SLEEP_OFF = 0
    const val SLEEP_END_OF_TRACK = -1

    /** Session extras published by the service so every controller sees the timer. */
    const val EXTRA_SLEEP_AT = "sleep_at"
    const val EXTRA_SLEEP_END_OF_TRACK = "sleep_end_of_track"
}
