package com.danielzuniga.player.data

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.danielzuniga.player.R
import com.danielzuniga.player.data.tags.TagFixRepository
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
    private val tagFixes: TagFixRepository,
    private val scope: CoroutineScope,
) {
    /** Songs exactly as the last scan read them; [library] shows them with [tagFixes] applied. */
    private var scanned: List<Song> = emptyList()
    private var identifyJob: Job? = null

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
        // A song identified or restored: show it right away.
        scope.launch {
            tagFixes.entries.collect { if (_hasScanned.value) publish() }
        }
        scope.launch {
            settings.onlineTags.collect { enabled -> if (enabled && _hasScanned.value) identifyUntagged() }
        }
        scope.launch {
            var first = true
            settings.minDurationSec.collect {
                if (!first && _hasScanned.value) scan()
                first = false
            }
        }
    }

    /**
     * "Unknown artist/album" in the language the UI is showing. The activity passes them in:
     * with an in-app language on Android 12 and older only the activity follows it, not the app
     * context. A different pair (the language changed) rescans so the library matches.
     */
    @Volatile private var labels: Pair<String, String>? = null
    @Volatile private var scannedLabels: Pair<String, String>? = null

    fun setUnknownLabels(artist: String, album: String) {
        labels = artist to album
        if (_hasScanned.value && scannedLabels != labels) scope.launch { scan() }
    }

    private fun unknownLabels(): Pair<String, String> =
        (labels ?: (context.getString(R.string.unknown_artist) to context.getString(R.string.unknown_album)))
            .also { scannedLabels = it }

    /** Scans once; later calls are no-ops unless [force] is set. */
    fun load(force: Boolean = false) {
        if (_hasScanned.value && !force) return
        scope.launch { scan(onlyIfNeeded = !force) }
    }

    /** Returns the library, scanning first if that never happened (used by the playback service). */
    suspend fun awaitLibrary(): LibraryIndex {
        // Once scanned, a rescan in progress doesn't hold up playback: the current library will do.
        if (!_hasScanned.value) scan(onlyIfNeeded = true)
        return _library.value
    }

    private suspend fun scan(onlyIfNeeded: Boolean = false) = scanMutex.withLock {
        if (onlyIfNeeded && _hasScanned.value) return@withLock
        _isScanning.value = true
        try {
            val songs = querySongs(settings.minDurationSec.first())
            tagFixes.awaitLoaded()
            scanned = songs
            publish()
            _hasScanned.value = true
            identifyUntagged()
            registerObserver()
        } catch (_: SecurityException) {
            // Permission not granted yet; the UI asks for it and triggers another load.
        } finally {
            _isScanning.value = false
        }
    }

    private val publishMutex = Mutex()

    /**
     * Builds the index off the main thread (grouping a big library is real work, and it runs again
     * whenever a song is identified). The lock makes each build read the latest scan and fixes, so
     * an older build can't overwrite a newer one.
     */
    private suspend fun publish() = publishMutex.withLock {
        val songs = scanned
        _library.value = withContext(Dispatchers.Default) { LibraryIndex(songs.map(tagFixes::apply)) }
    }

    /** Looks up untagged songs in the background; the library updates as each one is found. */
    private fun identifyUntagged() {
        identifyJob?.cancel()
        val songs = scanned
        identifyJob = scope.launch { tagFixes.identifyUntagged(songs) }
    }

    /** Back to the file's own tags for [songId]. */
    fun restoreTags(songId: Long) {
        scope.launch { tagFixes.restore(songId) }
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
        val (unknownArtist, unknownAlbum) = unknownLabels()

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
            MediaStore.Audio.Media.DATA,
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
            val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)

            buildList(cursor.count) {
                while (cursor.moveToNext()) {
                    // Tags are messy ("A, ,, B", NUL-separated lists, ALL CAPS): clean them once here.
                    val taggedArtists = cursor.getString(artistCol).takeUnless { it == MediaStore.UNKNOWN_STRING }
                        .let(TagText::artists)
                    val artists = taggedArtists.ifEmpty { listOf(unknownArtist) }
                    val album = TagText.title(cursor.getString(albumCol)).takeUnless { it.isBlank() || it == MediaStore.UNKNOWN_STRING }
                    add(
                        Song(
                            id = cursor.getLong(idCol),
                            title = TagText.title(cursor.getString(titleCol)),
                            artist = TagText.joinArtists(artists),
                            artists = artists,
                            album = album ?: unknownAlbum,
                            albumId = cursor.getLong(albumIdCol),
                            durationMs = cursor.getLong(durationCol),
                            track = cursor.getInt(trackCol),
                            year = cursor.getInt(yearCol),
                            dateAddedSec = cursor.getLong(dateAddedCol),
                            path = if (dataCol >= 0) cursor.getString(dataCol).orEmpty() else "",
                            hasArtistTag = taggedArtists.isNotEmpty(),
                            hasAlbumTag = album != null,
                        )
                    )
                }
            }
        } ?: emptyList()
    }

    private companion object {
        const val RESCAN_DEBOUNCE_MS = 1_500L
    }
}
