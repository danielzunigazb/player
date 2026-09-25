package com.danielzuniga.player.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FilterSongsTest {

    private val songs = listOf(
        song(1, "Canción del Mariachi", "Los Lobos", "Desperado"),
        song(2, "Bohemian Rhapsody", "Queen", "A Night at the Opera"),
        song(3, "Oye Cómo Va", "Santana", "Abraxas"),
    )

    @Test
    fun blankQueryReturnsEverything() {
        assertEquals(songs, filterSongs(songs, "   "))
    }

    @Test
    fun matchesIgnoringCaseAndAccents() {
        assertEquals(listOf(1L), filterSongs(songs, "CANCION").map { it.id })
        assertEquals(listOf(3L), filterSongs(songs, "como va").map { it.id })
    }

    @Test
    fun matchesArtistAndAlbum() {
        assertEquals(listOf(2L), filterSongs(songs, "queen").map { it.id })
        assertEquals(listOf(3L), filterSongs(songs, "abrax").map { it.id })
    }

    @Test
    fun noMatchReturnsEmpty() {
        assertEquals(emptyList<Song>(), filterSongs(songs, "metallica"))
    }

    private fun song(id: Long, title: String, artist: String, album: String) =
        Song(id = id, title = title, artist = artist, album = album, albumId = id, durationMs = 180_000)
}
