package com.danielzuniga.player

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.MediaStore
import org.robolectric.Robolectric

data class FakeSong(
    val id: Long,
    val title: String,
    val artist: String = "Artist",
    val album: String = "Album",
    val albumId: Long = 1,
    val track: Int = 1,
    val path: String = "/storage/emulated/0/Music/$album/$title.mp3",
)

/** Stands in for the system MediaStore so tests can scan a known library. */
class FakeMediaProvider : ContentProvider() {
    override fun onCreate() = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val cursor = MatrixCursor(
            arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.TRACK,
                MediaStore.Audio.Media.YEAR,
                MediaStore.Audio.Media.DATE_ADDED,
                MediaStore.Audio.Media.DATA,
            )
        )
        songs.forEach {
            cursor.addRow(arrayOf<Any>(it.id, it.title, it.artist, it.album, it.albumId, 200_000L, it.track, 2020, it.id, it.path))
        }
        return cursor
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    companion object {
        private var songs: List<FakeSong> = emptyList()

        fun install(songs: List<FakeSong>) {
            this.songs = songs
            Robolectric.buildContentProvider(FakeMediaProvider::class.java).create(MediaStore.AUTHORITY)
        }
    }
}
