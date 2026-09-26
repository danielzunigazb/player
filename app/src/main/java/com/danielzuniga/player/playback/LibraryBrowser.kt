package com.danielzuniga.player.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.danielzuniga.player.AppContainer
import com.danielzuniga.player.R
import com.danielzuniga.player.data.LibraryIndex
import com.danielzuniga.player.data.Song
import com.danielzuniga.player.data.filterSongs
import com.danielzuniga.player.data.songUri
import kotlinx.coroutines.flow.first

/**
 * The browsable tree shown by Android Auto and other media browsers.
 *
 * Browsable nodes use ids like `albums` or `album:42`. Playable songs are `<parentId>|<songId>`
 * so that picking one can queue its whole album or playlist; they are normalized to plain
 * song ids (as used by the app UI) before reaching the player.
 */
class LibraryBrowser(private val context: Context, private val container: AppContainer) {

    fun root(): MediaItem = folder(ROOT, context.getString(R.string.app_name))

    suspend fun children(parentId: String): List<MediaItem>? {
        val library = container.musicRepository.awaitLibrary()
        return when {
            parentId == ROOT -> listOf(
                folder(SONGS, context.getString(R.string.tab_songs)),
                folder(ALBUMS, context.getString(R.string.tab_albums)),
                folder(ARTISTS, context.getString(R.string.tab_artists)),
                folder(PLAYLISTS, context.getString(R.string.tab_playlists)),
            )
            parentId == ALBUMS -> library.albums.map {
                folder("$ALBUM${it.id}", it.title, subtitle = it.artist, mediaType = MediaMetadata.MEDIA_TYPE_ALBUM)
            }
            parentId == ARTISTS -> library.artists.map {
                folder(artistId(it.name), it.name, mediaType = MediaMetadata.MEDIA_TYPE_ARTIST)
            }
            parentId == PLAYLISTS -> smartPlaylists() + container.userData.playlistSummaries.first().map {
                folder("$PLAYLIST${it.id}", it.name, mediaType = MediaMetadata.MEDIA_TYPE_PLAYLIST)
            }
            else -> songsOf(parentId, library)?.map { it.toBrowsableSong(parentId) }
        }
    }

    suspend fun item(id: String): MediaItem? {
        if (id == ROOT) return root()
        val library = container.musicRepository.awaitLibrary()
        songIdOf(id)?.let { songId -> return library.song(songId)?.toBrowsableSong(id.substringBeforeLast('|')) }
        return children(ROOT)?.firstOrNull { it.mediaId == id }
    }

    suspend fun search(query: String): List<MediaItem> {
        val library = container.musicRepository.awaitLibrary()
        return filterSongs(library.songs, query).take(SEARCH_LIMIT).map { it.toBrowsableSong(SEARCH) }
    }

    /**
     * Turns what a browser asked to play into the queue the player gets. A single song picked
     * from a list expands into that whole list, starting at the chosen song.
     */
    suspend fun resolveQueue(items: List<MediaItem>, startIndex: Int): Pair<List<MediaItem>, Int> {
        val library = container.musicRepository.awaitLibrary()
        // Voice requests ("play X") arrive as an item carrying only a search query.
        items.singleOrNull()?.requestMetadata?.searchQuery?.let { query ->
            val results = searchResults(query, library)
            if (results.isNotEmpty()) return results.map { it.toMediaItem() } to 0
        }
        if (items.size == 1) {
            val id = items.single().mediaId
            val songId = songIdOf(id)
            val parent = id.substringBeforeLast('|', missingDelimiterValue = "")
            val siblings = if (songId != null && parent.isNotEmpty()) songsOf(parent, library) else null
            val index = siblings?.indexOfFirst { it.id == songId } ?: -1
            if (siblings != null && index >= 0) return siblings.map { it.toMediaItem() } to index
        }
        return items.map { resolveItem(it, library) } to startIndex
    }

    /** Fills in URI and metadata for items that arrive with only an id. */
    fun resolveItem(item: MediaItem, library: LibraryIndex = container.musicRepository.library.value): MediaItem {
        val songId = songIdOf(item.mediaId) ?: return item
        library.song(songId)?.let { return it.toMediaItem() }
        return if (item.localConfiguration != null) {
            item
        } else {
            item.buildUpon().setMediaId(songId.toString()).setUri(songUri(songId)).build()
        }
    }

    private suspend fun songsOf(parentId: String, library: LibraryIndex): List<Song>? = when {
        parentId == SONGS -> library.songs
        parentId == SEARCH -> null
        parentId.startsWith(ALBUM) -> parentId.removePrefix(ALBUM).toLongOrNull()?.let(library::album)?.songs
        parentId.startsWith(ARTIST) -> library.artist(Uri.decode(parentId.removePrefix(ARTIST)))?.songs
        parentId.startsWith(PLAYLIST) -> parentId.removePrefix(PLAYLIST).toLongOrNull()
            ?.let { library.songs(container.userData.playlistSongIds(it).first()) }
        parentId == SMART_FAVORITES -> library.songs(container.userData.favoriteIds.first())
        parentId == SMART_MOST_PLAYED -> library.songs(container.userData.mostPlayedIds().first())
        else -> null
    }

    private fun smartPlaylists() = listOf(
        folder(SMART_FAVORITES, context.getString(R.string.favorites), mediaType = MediaMetadata.MEDIA_TYPE_PLAYLIST),
        folder(SMART_MOST_PLAYED, context.getString(R.string.most_played), mediaType = MediaMetadata.MEDIA_TYPE_PLAYLIST),
    )

    private fun Song.toBrowsableSong(parentId: String): MediaItem =
        toMediaItem().buildUpon().setMediaId("$parentId|$id").build()

    private fun folder(
        id: String,
        title: String,
        subtitle: String? = null,
        mediaType: Int = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
    ): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(mediaType)
                .build()
        )
        .build()

    companion object {
        /** Songs for a spoken or typed request; an empty request means "play something". */
        fun searchResults(query: String, library: LibraryIndex): List<Song> =
            if (query.isBlank()) library.songs.shuffled() else filterSongs(library.songs, query)

        const val ROOT = "root"
        const val SONGS = "songs"
        const val ALBUMS = "albums"
        const val ARTISTS = "artists"
        const val PLAYLISTS = "playlists"
        const val SEARCH = "search"
        const val ALBUM = "album:"
        const val ARTIST = "artist:"
        const val PLAYLIST = "playlist:"
        const val SMART_FAVORITES = "smart:favorites"
        const val SMART_MOST_PLAYED = "smart:most_played"
        private const val SEARCH_LIMIT = 50

        /**
         * An artist's browse id. The name is encoded: a "|" in it ("Blink|182") would otherwise be
         * read as the separator before a song id.
         */
        fun artistId(name: String): String = ARTIST + Uri.encode(name)

        /** Song id from either a plain id ("42") or a browse id ("album:7|42"). */
        fun songIdOf(mediaId: String): Long? = mediaId.substringAfterLast('|').toLongOrNull()
    }
}
