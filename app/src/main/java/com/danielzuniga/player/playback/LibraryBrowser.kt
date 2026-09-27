package com.danielzuniga.player.playback

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import com.danielzuniga.player.AppContainer
import com.danielzuniga.player.R
import com.danielzuniga.player.data.LibraryIndex
import com.danielzuniga.player.data.Song
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
        return library.filterSongs(query).take(SEARCH_LIMIT).map { it.toBrowsableSong(SEARCH) }
    }

    /**
     * Turns what a browser asked to play into the queue the player gets. A single song picked
     * from a list expands into that whole list, starting at the chosen song. [startPositionMs]
     * stays with the chosen item; if that can't play, the next one starts from its beginning.
     * Throws [NothingToPlayException] when nothing in the request can play.
     */
    @OptIn(UnstableApi::class)
    suspend fun resolveQueue(items: List<MediaItem>, startIndex: Int, startPositionMs: Long): MediaItemsWithStartPosition {
        val library = container.musicRepository.awaitLibrary()
        // Voice requests ("play X") arrive as an item carrying only a search query.
        items.singleOrNull()?.requestMetadata?.searchQuery?.let { query ->
            val results = searchResults(query, library)
            if (results.isNotEmpty()) return MediaItemsWithStartPosition(results.map { it.toMediaItem() }, 0, C.TIME_UNSET)
        }
        if (items.size == 1) {
            val id = items.single().mediaId
            val songId = songIdOf(id)
            val parent = id.substringBeforeLast('|', missingDelimiterValue = "")
            val siblings = if (songId != null && parent.isNotEmpty()) songsOf(parent, library) else null
            val index = siblings?.indexOfFirst { it.id == songId } ?: -1
            if (siblings != null && index >= 0) {
                return MediaItemsWithStartPosition(siblings.map { it.toMediaItem() }, index, startPositionMs)
            }
        }
        val resolved = items.map { resolveItem(it, library) }
        val playable = playable(resolved)
        if (startIndex < 0 || playable.size == resolved.size) return MediaItemsWithStartPosition(playable, startIndex, startPositionMs)
        // The chosen item moves up by the ones dropped before it; if it was dropped, the next one plays.
        val index = resolved.take(startIndex).count { it.localConfiguration != null }.coerceAtMost(playable.lastIndex)
        val chosenPlays = resolved.getOrNull(startIndex)?.localConfiguration != null
        return MediaItemsWithStartPosition(playable, index, if (chosenPlays) startPositionMs else C.TIME_UNSET)
    }

    /**
     * [items] without the ones that have nothing to play (no URI: a voice request that matched
     * nothing, a folder); the player would throw on those where nobody hears it. Throws
     * [NothingToPlayException] when none are left, so the request fails and the caller is told.
     */
    fun playable(items: List<MediaItem>): List<MediaItem> =
        items.filter { it.localConfiguration != null }.ifEmpty { throw NothingToPlayException() }

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
            if (query.isBlank()) library.songs.shuffled() else library.filterSongs(query)

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

/** Nothing in a request to the session can play. */
class NothingToPlayException : Exception("Nothing to play")
