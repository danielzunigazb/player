package com.danielzuniga.player.data.tags

import com.danielzuniga.player.data.Song
import com.danielzuniga.player.data.TagText
import com.danielzuniga.player.data.db.TagFixDao
import com.danielzuniga.player.data.db.TagFixEntity
import com.danielzuniga.player.data.lyrics.LrcLibClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Better artist and title for songs whose files lack them, kept in the app's database on top of
 * the scan; the audio files are never modified.
 *
 * - Files without an artist tag, whose title is usually the file name ("24K - T3R Elemento"),
 *   are looked up on LRCLIB with both readings of the name. Only a match on title, artist and
 *   length is used, spelled the way the catalogue spells it. No match is remembered too.
 * - Offline and immediate: download noise is dropped from those names ("01. ", "(Official
 *   Video)"), and a tagged song's title that repeats its artist ("Soda Stereo - …") loses it.
 *
 * Every change can be undone per song with [restore], which also stops it from coming back.
 */
class TagFixRepository(
    private val dao: TagFixDao,
    private val client: LrcLibClient,
    private val onlineEnabled: () -> Boolean,
    scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
) {

    private val _entries = MutableStateFlow<Map<Long, TagFixEntity>>(emptyMap())
    /** What's known per song id; changes whenever a song is identified or restored. */
    val entries: StateFlow<Map<Long, TagFixEntity>> = _entries.asStateFlow()

    private val loaded = CompletableDeferred<Unit>()

    init {
        scope.launch {
            dao.observeAll().collect { rows ->
                _entries.value = rows.associateBy { it.songId }
                loaded.complete(Unit)
            }
        }
    }

    /** Suspends until the saved fixes are loaded, so the first library already shows them. */
    suspend fun awaitLoaded() = loaded.await()

    /** [song] as the app should show it. */
    fun apply(song: Song): Song = apply(song, _entries.value[song.id])

    /**
     * Looks up the untagged songs of [songs] that were never looked up. Stops at the first
     * network failure; the next scan tries again.
     */
    suspend fun identifyUntagged(songs: List<Song>) {
        awaitLoaded()
        for (song in songs) {
            if (!onlineEnabled()) return
            if (song.hasArtistTag || _entries.value.containsKey(song.id)) continue
            val readings = TitleSplit.readings(song.title)
            if (readings.isEmpty()) continue
            val identity = withContext(Dispatchers.IO) { client.identify(readings, song.durationMs) }
            val entry = when (identity) {
                is LrcLibClient.Identity.Found ->
                    TagFixEntity(song.id, TagFixEntity.FIXED, identity.title, identity.artist, now())
                LrcLibClient.Identity.NotFound ->
                    TagFixEntity(song.id, TagFixEntity.NO_MATCH, null, null, now())
                LrcLibClient.Identity.Failed -> return
            }
            dao.upsert(entry)
        }
    }

    /** Back to the file's own tags for good. */
    suspend fun restore(songId: Long) {
        dao.upsert(TagFixEntity(songId, TagFixEntity.RESTORED, null, null, now()))
    }

    companion object {
        internal fun apply(song: Song, entry: TagFixEntity?): Song {
            if (entry?.status == TagFixEntity.RESTORED) return song
            // Only while the file still lacks an artist: tags written later by the user win.
            if (entry?.status == TagFixEntity.FIXED && !song.hasArtistTag && entry.title != null && entry.artist != null) {
                val artists = TagText.artists(entry.artist).ifEmpty { return song }
                return song.copy(
                    title = TagText.title(entry.title).ifEmpty { song.title },
                    artist = TagText.joinArtists(artists),
                    artists = artists,
                    hasArtistTag = true,
                    tagsFixed = true,
                )
            }
            if (!song.hasArtistTag) {
                val cleaned = TitleSplit.clean(song.title)
                return if (cleaned.isNotEmpty() && cleaned != song.title) song.copy(title = cleaned, tagsFixed = true) else song
            }
            val trimmed = TitleSplit.withoutArtistPrefix(song.title, song.artists.first())
                ?: TitleSplit.withoutArtistPrefix(song.title, song.artist)
            return if (trimmed != null) song.copy(title = trimmed, tagsFixed = true) else song
        }
    }
}
