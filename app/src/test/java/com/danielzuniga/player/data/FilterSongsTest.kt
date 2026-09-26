package com.danielzuniga.player.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FilterSongsTest {

    private val songs = listOf(
        song(1, "Canción del Mariachi", "Los Lobos", "Desperado"),
        song(2, "Bohemian Rhapsody", "Queen", "A Night at the Opera"),
        song(3, "Oye Cómo Va", "Santana", "Abraxas"),
    )
    private val index = LibraryIndex(songs)

    @Test
    fun blankQueryReturnsEverything() {
        assertEquals(songs, index.filterSongs("   "))
    }

    @Test
    fun matchesIgnoringCaseAndAccents() {
        assertEquals(listOf(1L), index.filterSongs("CANCION").map { it.id })
        assertEquals(listOf(3L), index.filterSongs("como va").map { it.id })
    }

    @Test
    fun matchesArtistAndAlbum() {
        assertEquals(listOf(2L), index.filterSongs("queen").map { it.id })
        assertEquals(listOf(3L), index.filterSongs("abrax").map { it.id })
    }

    @Test
    fun noMatchReturnsEmpty() {
        assertEquals(emptyList<Song>(), index.filterSongs("metallica"))
    }

    @Test
    fun normalizesEachSongOnceNotOnEveryKeystroke() {
        var normalized = 0
        val search = SearchIndex(songs) { normalized++; listOf(it.title) }
        listOf("c", "ca", "can", "canc").forEach(search::filter)
        assertEquals(songs.size, normalized)
    }

    private fun song(id: Long, title: String, artist: String, album: String) =
        Song(id = id, title = title, artist = artist, album = album, albumId = id, durationMs = 180_000)
}
