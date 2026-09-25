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
) {
    val uri: Uri get() = songUri(id)
    val artworkUri: Uri get() = ContentUris.withAppendedId(ALBUM_ART_BASE_URI, albumId)
}

private val ALBUM_ART_BASE_URI: Uri = "content://media/external/audio/albumart".toUri()

fun songUri(id: Long): Uri =
    ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
