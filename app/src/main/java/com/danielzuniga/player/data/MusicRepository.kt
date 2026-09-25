package com.danielzuniga.player.data

import android.content.Context
import android.provider.MediaStore
import com.danielzuniga.player.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MusicRepository(private val context: Context) {

    suspend fun loadSongs(): List<Song> = withContext(Dispatchers.IO) {
        val unknownArtist = context.getString(R.string.unknown_artist)
        val unknownAlbum = context.getString(R.string.unknown_album)

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
        )
        val selection =
            "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= ?"
        val selectionArgs = arrayOf(MIN_DURATION_MS.toString())
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder,
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            buildList(cursor.count) {
                while (cursor.moveToNext()) {
                    add(
                        Song(
                            id = cursor.getLong(idCol),
                            title = cursor.getString(titleCol).orEmpty(),
                            artist = cursor.getString(artistCol).orUnknown(unknownArtist),
                            album = cursor.getString(albumCol).orUnknown(unknownAlbum),
                            albumId = cursor.getLong(albumIdCol),
                            durationMs = cursor.getLong(durationCol),
                        )
                    )
                }
            }
        } ?: emptyList()
    }

    private fun String?.orUnknown(fallback: String): String =
        if (isNullOrBlank() || this == MediaStore.UNKNOWN_STRING) fallback else this

    private companion object {
        const val MIN_DURATION_MS = 10_000L
    }
}
