package com.danielzuniga.player.data

data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val year: Int,
    val songs: List<Song>,
) {
    val durationMs: Long get() = songs.sumOf { it.durationMs }
}

data class Artist(
    val name: String,
    val albums: List<Album>,
    val songs: List<Song>,
)

data class Folder(
    val path: String,
    val name: String,
    val songs: List<Song>,
)

enum class SongSort { TITLE, ARTIST, ALBUM, RECENT, DURATION }

/** All derived views of the device library, computed once per scan. */
class LibraryIndex(val songs: List<Song>) {

    private val byId: Map<Long, Song> = songs.associateBy { it.id }

    val albums: List<Album> = songs
        .groupBy { it.albumId }
        .map { (albumId, albumSongs) ->
            val first = albumSongs.first()
            Album(
                id = albumId,
                title = first.album,
                // The album's lead artist: the one most tracks credit first, not a long collab list.
                artist = albumSongs.groupingBy { it.artists.first() }.eachCount().maxBy { it.value }.key,
                year = albumSongs.maxOf { it.year },
                songs = albumSongs.sortedWith(compareBy<Song> { it.track }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }),
            )
        }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })

    private val albumPosition: Map<Long, Int> = albums.withIndex().associate { (i, album) -> album.id to i }

    // A collaboration counts for every artist it credits, so "A, B" shows up under A and under B.
    val artists: List<Artist> = songs
        .flatMap { song -> song.artists.map { it to song } }
        .groupBy({ it.first }, { it.second })
        .map { (name, artistSongs) ->
            Artist(
                name = name,
                // Positions in [albums] keep its title order, without scanning every album per artist.
                albums = artistSongs.mapTo(sortedSetOf()) { albumPosition.getValue(it.albumId) }
                    .map(albums::get)
                    .sortedByDescending { it.year },
                songs = artistSongs.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }),
            )
        }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

    val folders: List<Folder> = songs
        .filter { it.folder.isNotEmpty() }
        .groupBy { it.folder }
        .map { (path, folderSongs) ->
            Folder(
                path = path,
                name = path.substringAfterLast('/'),
                songs = folderSongs.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.path }),
            )
        }
        .sortedWith(compareBy<Folder, String>(String.CASE_INSENSITIVE_ORDER) { it.name }.thenBy { it.path })

    private val songSearch = SearchIndex(songs) { listOf(it.title, it.artist, it.album) }
    private val albumSearch = SearchIndex(albums) { listOf(it.title, it.artist) }
    private val artistSearch = SearchIndex(artists) { listOf(it.name) }
    private val folderSearch = SearchIndex(folders) { listOf(it.name) }

    fun filterSongs(query: String): List<Song> = songSearch.filter(query)
    fun filterAlbums(query: String): List<Album> = albumSearch.filter(query)
    fun filterArtists(query: String): List<Artist> = artistSearch.filter(query)
    fun filterFolders(query: String): List<Folder> = folderSearch.filter(query)

    fun song(id: Long): Song? = byId[id]

    fun folder(path: String): Folder? = folders.firstOrNull { it.path == path }

    fun songs(ids: List<Long>): List<Song> = ids.mapNotNull { byId[it] }

    fun album(id: Long): Album? = albumPosition[id]?.let(albums::get)

    fun artist(name: String): Artist? = artists.firstOrNull { it.name == name }

    companion object {
        val EMPTY = LibraryIndex(emptyList())
    }
}

fun List<Song>.sortedBy(sort: SongSort): List<Song> = when (sort) {
    SongSort.TITLE -> sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    SongSort.ARTIST -> sortedWith(
        compareBy<Song, String>(String.CASE_INSENSITIVE_ORDER) { it.artist }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.album }
            .thenBy { it.track }
    )
    SongSort.ALBUM -> sortedWith(
        compareBy<Song, String>(String.CASE_INSENSITIVE_ORDER) { it.album }.thenBy { it.track }
    )
    SongSort.RECENT -> sortedByDescending { it.dateAddedSec }
    SongSort.DURATION -> sortedByDescending { it.durationMs }
}
