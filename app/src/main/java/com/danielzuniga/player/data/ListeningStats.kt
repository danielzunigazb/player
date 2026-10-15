package com.danielzuniga.player.data

import com.danielzuniga.player.data.db.PlayStatEntity

data class RankedSong(val song: Song, val plays: Int)

/** [songs] is how many of this artist's songs have been played at least once. */
data class RankedArtist(val name: String, val plays: Int, val songs: Int)

data class RankedAlbum(val id: Long, val title: String, val artist: String, val plays: Int)

/**
 * What the play counts say about the library: totals and the most played songs, artists and albums.
 *
 * A play is counted the moment a song starts, so a song skipped after two seconds counts like one
 * heard to the end. That's why this has no "time listened": plays × length would be a number the
 * data can't back. Only songs in the library right now are counted (a card that's out, or a
 * song shorter than the minimum length, aren't), and what's listed is what [compute] is given.
 */
data class ListeningStats(
    val totalPlays: Int,
    val songsHeard: Int,
    val topSongs: List<RankedSong>,
    val topArtists: List<RankedArtist>,
    val topAlbums: List<RankedAlbum>,
) {
    val isEmpty: Boolean get() = totalPlays == 0

    companion object {
        val EMPTY = ListeningStats(0, 0, emptyList(), emptyList(), emptyList())

        /** The top [limit] of each, most played first, always in the same order for the same data. */
        fun compute(stats: List<PlayStatEntity>, library: LibraryIndex, limit: Int = 10): ListeningStats {
            val played = stats.mapNotNull { stat ->
                if (stat.playCount <= 0) return@mapNotNull null
                library.song(stat.songId)?.let { Played(it, stat.playCount, stat.lastPlayedAt) }
            }
            if (played.isEmpty()) return EMPTY

            val songs = played
                .sortedWith(compareByDescending<Played> { it.plays }.thenByDescending { it.lastPlayedAt }.thenBy { it.song.id })
                .take(limit)
                .map { RankedSong(it.song, it.plays) }

            // A song with two credited artists counts for both; one with no artist tag, for neither:
            // its "artist" is a placeholder, not a name.
            val artists = played
                .filter { it.song.hasArtistTag }
                .flatMap { p -> p.song.artists.distinct().map { it to p } }
                .groupBy({ it.first }, { it.second })
                .map { (name, plays) -> RankedArtist(name, plays.sumOf { it.plays }, plays.size) }
                .sortedWith(compareByDescending<RankedArtist> { it.plays }.thenBy { it.name.lowercase() }.thenBy { it.name })
                .take(limit)

            val albums = played
                .filter { it.song.hasAlbumTag }
                .groupBy { it.song.albumId }
                .map { (id, plays) ->
                    val first = plays.first().song
                    RankedAlbum(id, first.album, first.artist, plays.sumOf { it.plays })
                }
                .sortedWith(compareByDescending<RankedAlbum> { it.plays }.thenBy { it.title.lowercase() }.thenBy { it.id })
                .take(limit)

            return ListeningStats(
                totalPlays = played.sumOf { it.plays },
                songsHeard = played.size,
                topSongs = songs,
                topArtists = artists,
                topAlbums = albums,
            )
        }
    }

    private class Played(val song: Song, val plays: Int, val lastPlayedAt: Long)
}
