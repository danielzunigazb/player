package com.danielzuniga.player.data

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.danielzuniga.player.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class MusicRepository(
    private val context: Context,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
) {
    private val _library = MutableStateFlow(LibraryIndex.EMPTY)
    val library: StateFlow<LibraryIndex> = _library.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _hasScanned = MutableStateFlow(false)
    val hasScanned: StateFlow<Boolean> = _hasScanned.asStateFlow()

    private val scanMutex = Mutex()
    private var observerRegistered = false
    private var pendingRescan: Job? = null

    // MediaStore fires bursts of change notifications while files are copied; debounce them.
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            pendingRescan?.cancel()
            pendingRescan = scope.launch {
                delay(RESCAN_DEBOUNCE_MS)
                scan()
            }
        }
    }

    init {
        scope.launch {
            var first = true
            settings.minDurationSec.collect {
                if (!first && _hasScanned.value) scan()
                first = false
            }
        }
    }

    /** Scans once; later calls are no-ops unless [force] is set. */
    fun load(force: Boolean = false) {
        if (_hasScanned.value && !force) return
        scope.launch { scan(onlyIfNeeded = !force) }
    }

    /** Returns the library, scanning first if that never happened (used by the playback service). */
    suspend fun awaitLibrary(): LibraryIndex {
        scan(onlyIfNeeded = true)
        return _library.value
    }

    private suspend fun scan(onlyIfNeeded: Boolean = false) = scanMutex.withLock {
        if (onlyIfNeeded && _hasScanned.value) return@withLock
        _isScanning.value = true
        try {
            val songs = querySongs(settings.minDurationSec.first())
            _library.value = LibraryIndex(songs)
            _hasScanned.value = true
            registerObserver()
        } catch (_: SecurityException) {
            // Permission not granted yet; the UI asks for it and triggers another load.
        } finally {
            _isScanning.value = false
        }
    }

    private fun registerObserver() {
        if (observerRegistered) return
        context.contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            true,
            observer,
        )
        observerRegistered = true
    }

    private suspend fun querySongs(minDurationSec: Int): List<Song> = withContext(Dispatchers.IO) {
        val unknownArtist = context.getString(R.string.unknown_artist)
        val unknownAlbum = context.getString(R.string.unknown_album)

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DATE_ADDED,
        )
        val selection =
            "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= ?"
        val selectionArgs = arrayOf((minDurationSec * 1000L).toString())
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
            val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val yearCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

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
                            track = cursor.getInt(trackCol),
                            year = cursor.getInt(yearCol),
                            dateAddedSec = cursor.getLong(dateAddedCol),
                        )
                    )
                }
            }
        } ?: emptyList()
    }

    private fun String?.orUnknown(fallback: String): String =
        if (isNullOrBlank() || this == MediaStore.UNKNOWN_STRING) fallback else this

    private companion object {
        const val RESCAN_DEBOUNCE_MS = 1_500L
    }
}
