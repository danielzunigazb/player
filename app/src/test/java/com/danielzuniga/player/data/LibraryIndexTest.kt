package com.danielzuniga.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryIndexTest {

    private val songs = listOf(
        song(1, "Zeta", "Soda Stereo", "Canción Animal", albumId = 10, track = 2, year = 1990, added = 300),
        song(2, "Alfa", "Soda Stereo", "Canción Animal", albumId = 10, track = 1, year = 1990, added = 100),
        song(3, "Beta", "Soda Stereo", "Signos", albumId = 11, track = 1, year = 1986, added = 200, duration = 400_000),
        song(4, "Gamma", "Café Tacvba", "Re", albumId = 12, track = 1, year = 1994, added = 50),
    )
    private val index = LibraryIndex(songs)

    @Test
    fun groupsAlbumsSortedByTitleWithTracksInOrder() {
        assertEquals(listOf("Canción Animal", "Re", "Signos"), index.albums.map { it.title })
        assertEquals(listOf(2L, 1L), index.album(10)!!.songs.map { it.id })
    }

    @Test
    fun groupsArtistsWithNewestAlbumFirst() {
        assertEquals(listOf("Café Tacvba", "Soda Stereo"), index.artists.map { it.name })
        val soda = index.artist("Soda Stereo")!!
        assertEquals(listOf(10L, 11L), soda.albums.map { it.id })
        assertEquals(3, soda.songs.size)
    }

    @Test
    fun resolvesIdsSkippingMissingSongs() {
        assertEquals(listOf(3L, 1L), index.songs(listOf(3, 99, 1)).map { it.id })
        assertNull(index.song(99))
    }

    @Test
    fun sortsSongs() {
        assertEquals(listOf(2L, 3L, 4L, 1L), songs.sortedBy(SongSort.TITLE).map { it.id })
        assertEquals(listOf(1L, 3L, 2L, 4L), songs.sortedBy(SongSort.RECENT).map { it.id })
        assertEquals(3L, songs.sortedBy(SongSort.DURATION).first().id)
        assertEquals(listOf(4L, 2L, 1L, 3L), songs.sortedBy(SongSort.ARTIST).map { it.id })
    }

    @Test
    fun filtersAlbumsAndArtistsIgnoringAccents() {
        assertEquals(listOf(10L), filterAlbums(index.albums, "cancion").map { it.id })
        assertEquals(listOf("Café Tacvba"), filterArtists(index.artists, "CAFE").map { it.name })
        assertEquals(index.albums, filterAlbums(index.albums, " "))
    }

    @Test
    fun groupsFoldersByParentDirectory() {
        val index = LibraryIndex(
            listOf(
                Song(1, "b", "x", "y", 1, 1, path = "/sdcard/Music/Rock/b.mp3"),
                Song(2, "a", "x", "y", 1, 1, path = "/sdcard/Music/Rock/a.mp3"),
                Song(3, "c", "x", "y", 1, 1, path = "/sdcard/Download/c.mp3"),
                Song(4, "d", "x", "y", 1, 1, path = ""),
            )
        )
        assertEquals(listOf("Download", "Rock"), index.folders.map { it.name })
        assertEquals(listOf(2L, 1L), index.folder("/sdcard/Music/Rock")!!.songs.map { it.id })
        assertEquals(listOf("Rock"), filterFolders(index.folders, "roc").map { it.name })
    }

    private fun song(
        id: Long,
        title: String,
        artist: String,
        album: String,
        albumId: Long,
        track: Int,
        year: Int,
        added: Long,
        duration: Long = 200_000,
    ) = Song(id, title, artist, album, albumId, duration, track, year, added)
}
