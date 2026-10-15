package com.danielzuniga.player.data

import com.danielzuniga.player.data.db.PlayStatEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningStatsTest {

    private fun song(
        id: Long,
        title: String,
        artist: String = "Soda Stereo",
        album: String = "Signos",
        albumId: Long = 10,
        artists: List<String> = listOf(artist),
        hasArtistTag: Boolean = true,
        hasAlbumTag: Boolean = true,
    ) = Song(id, title, artist, album, albumId, 200_000, artists = artists, hasArtistTag = hasArtistTag, hasAlbumTag = hasAlbumTag)

    private fun stat(id: Long, plays: Int, last: Long = 0) = PlayStatEntity(id, plays, last)

    private fun compute(stats: List<PlayStatEntity>, vararg songs: Song, limit: Int = 10) =
        ListeningStats.compute(stats, LibraryIndex(songs.toList()), limit)

    @Test
    fun nothingPlayedIsEmpty() {
        assertTrue(compute(emptyList(), song(1, "A")).isEmpty)
        assertEquals(ListeningStats.EMPTY, compute(emptyList()))
        assertFalse(compute(listOf(stat(1, 1)), song(1, "A")).isEmpty)
    }

    @Test
    fun onlySongsInTheLibraryWithPlaysCount() {
        val result = compute(
            listOf(stat(1, 4), stat(2, 0), stat(3, -2), stat(99, 50)),
            song(1, "A"), song(2, "B"), song(3, "C"),
        )

        // 99 isn't in the library (card out, too short); 2 and 3 have no plays to speak of.
        assertEquals(4, result.totalPlays)
        assertEquals(1, result.songsHeard)
        assertEquals(listOf("A"), result.topSongs.map { it.song.title })
    }

    @Test
    fun songsRankByPlaysThenMostRecentThenId() {
        val result = compute(
            listOf(stat(1, 3, last = 100), stat(2, 5, last = 1), stat(3, 3, last = 900), stat(4, 3, last = 900)),
            song(1, "Una"), song(2, "Dos"), song(3, "Tres"), song(4, "Cuatro"),
        )

        assertEquals(listOf("Dos", "Tres", "Cuatro", "Una"), result.topSongs.map { it.song.title })
        assertEquals(listOf(5, 3, 3, 3), result.topSongs.map { it.plays })
        assertEquals(14, result.totalPlays)
        assertEquals(4, result.songsHeard)
    }

    @Test
    fun theLimitCutsEveryListButNotTheTotals() {
        val songs = (1L..5L).map { song(it, "S$it", artist = "Artista $it", album = "Álbum $it", albumId = it) }
        val result = compute(songs.map { stat(it.id, it.id.toInt()) }, *songs.toTypedArray(), limit = 2)

        assertEquals(2, result.topSongs.size)
        assertEquals(2, result.topArtists.size)
        assertEquals(2, result.topAlbums.size)
        assertEquals(15, result.totalPlays)
        assertEquals(5, result.songsHeard)
        assertEquals(listOf("S5", "S4"), result.topSongs.map { it.song.title })
    }

    @Test
    fun anArtistAddsUpHisSongsAcrossAlbumsAndCountsHowManyWerePlayed() {
        val result = compute(
            listOf(stat(1, 4), stat(2, 3), stat(3, 6)),
            song(1, "A", album = "Signos", albumId = 10),
            song(2, "B", album = "Canción Animal", albumId = 11),
            song(3, "C", artist = "Cerati", album = "Bocanada", albumId = 12),
        )

        assertEquals(
            listOf(RankedArtist("Soda Stereo", 7, 2), RankedArtist("Cerati", 6, 1)),
            result.topArtists,
        )
    }

    @Test
    fun aSongWithTwoCreditedArtistsCountsForBothAndOnceEach() {
        val result = compute(
            listOf(stat(1, 5)),
            song(1, "Duo", artist = "Soda Stereo, Cerati", artists = listOf("Soda Stereo", "Cerati", "Cerati")),
        )

        assertEquals(listOf("Cerati" to 5, "Soda Stereo" to 5), result.topArtists.map { it.name to it.plays })
        assertEquals(5, result.totalPlays)
    }

    @Test
    fun aSongWithoutAnArtistOrAlbumTagIsNotRankedAsAnArtistOrAlbumButStillCounts() {
        val result = compute(
            listOf(stat(1, 9), stat(2, 2)),
            song(1, "Sin etiquetas", artist = "Artista desconocido", album = "Álbum desconocido", albumId = 0, hasArtistTag = false, hasAlbumTag = false),
            song(2, "Con etiquetas"),
        )

        assertEquals(11, result.totalPlays)
        assertEquals(listOf("Sin etiquetas", "Con etiquetas"), result.topSongs.map { it.song.title })
        assertEquals(listOf("Soda Stereo"), result.topArtists.map { it.name })
        assertEquals(listOf("Signos"), result.topAlbums.map { it.title })
    }

    @Test
    fun anAlbumAddsUpItsSongsAndKeepsItsOwnName() {
        val result = compute(
            listOf(stat(1, 2), stat(2, 5), stat(3, 4)),
            song(1, "A", album = "Signos", albumId = 10),
            song(2, "B", album = "Signos", albumId = 10),
            song(3, "C", album = "Bocanada", albumId = 12, artist = "Cerati"),
        )

        assertEquals(
            listOf(RankedAlbum(10, "Signos", "Soda Stereo", 7), RankedAlbum(12, "Bocanada", "Cerati", 4)),
            result.topAlbums,
        )
    }

    @Test
    fun tiesBetweenArtistsAndAlbumsComeOutInTheSameOrderEveryTime() {
        val songs = arrayOf(
            song(1, "A", artist = "beta", album = "zeta", albumId = 3),
            song(2, "B", artist = "Alfa", album = "Eta", albumId = 2),
            song(3, "C", artist = "Gamma", album = "alfa", albumId = 1),
        )
        val stats = listOf(stat(1, 1), stat(2, 1), stat(3, 1))

        val forward = compute(stats, *songs)
        val backward = compute(stats.reversed(), *songs.reversedArray())

        assertEquals(listOf("Alfa", "beta", "Gamma"), forward.topArtists.map { it.name })
        assertEquals(listOf("alfa", "Eta", "zeta"), forward.topAlbums.map { it.title })
        assertEquals(forward, backward)
    }
}
