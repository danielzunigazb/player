package com.danielzuniga.player.data

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import androidx.core.net.toUri

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val track: Int = 0,
    val year: Int = 0,
    val dateAddedSec: Long = 0L,
    /** Absolute file path from MediaStore; may be blank or unreadable under scoped storage. */
    val path: String = "",
    /** Each credited artist on its own; [artist] is these joined for display. */
    val artists: List<String> = listOf(artist),
    /** False when the file has no artist tag and [artist] is the "unknown artist" label. */
    val hasArtistTag: Boolean = true,
    /** False when the file has no album tag and [album] is the "unknown album" label. */
    val hasAlbumTag: Boolean = true,
    /** Artist or title here differ from the file's tags (see TagFixRepository); can be restored. */
    val tagsFixed: Boolean = false,
) {
    val folder: String get() = path.substringBeforeLast('/', missingDelimiterValue = "")

    val uri: Uri get() = songUri(id)
    val artworkUri: Uri get() = albumArtUri(albumId)
}

private val ALBUM_ART_BASE_URI: Uri = "content://media/external/audio/albumart".toUri()

fun songUri(id: Long): Uri =
    ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

fun albumArtUri(albumId: Long): Uri = ContentUris.withAppendedId(ALBUM_ART_BASE_URI, albumId)
