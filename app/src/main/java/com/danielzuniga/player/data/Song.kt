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
) {
    val folder: String get() = path.substringBeforeLast('/', missingDelimiterValue = "")

    val uri: Uri get() = songUri(id)
    val artworkUri: Uri get() = albumArtUri(albumId)
}

private val ALBUM_ART_BASE_URI: Uri = "content://media/external/audio/albumart".toUri()

fun songUri(id: Long): Uri =
    ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

fun albumArtUri(albumId: Long): Uri = ContentUris.withAppendedId(ALBUM_ART_BASE_URI, albumId)
