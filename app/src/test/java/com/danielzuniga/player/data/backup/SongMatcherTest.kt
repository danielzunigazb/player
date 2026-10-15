package com.danielzuniga.player.data.backup

import com.danielzuniga.player.data.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SongMatcherTest {

    private fun song(
        id: Long,
        title: String,
        artist: String = "Soda Stereo",
        album: String = "Canción Animal",
        durationMs: Long = 210_000,
    ) = Song(id, title, artist, album, albumId = 10, durationMs = durationMs)

    private fun ref(
        title: String,
        artist: String = "Soda Stereo",
        album: String = "Canción Animal",
        durationMs: Long = 210_000,
    ) = SongRef(title, artist, album, durationMs)

    private fun find(ref: SongRef, vararg library: Song) = SongMatcher(library.toList()).find(ref)?.id

    @Test
    fun findsTheSongWhateverItsIdIs() {
        assertEquals(555L, find(ref("De Música Ligera"), song(1, "Persiana Americana"), song(555, "De Música Ligera")))
    }

    @Test
    fun accentsCaseAndFeaturedCreditsDontMatter() {
        assertEquals(1L, find(ref("De Musica Ligera"), song(1, "DE MÚSICA LIGERA")))
        assertEquals(1L, find(ref("Corazón (feat. Fito Páez)"), song(1, "Corazon")))
        assertEquals(1L, find(ref("Corazón"), song(1, "Corazón [feat. Alguien]")))
    }

    @Test
    fun aRemasteredOrRadioEditTailDoesntMakeItAnotherSongInEitherDirection() {
        assertEquals(1L, find(ref("Corazón"), song(1, "Corazón (Remastered 2007)")))
        assertEquals(1L, find(ref("Corazón (Remastered 2007)"), song(1, "Corazón")))
        assertEquals(1L, find(ref("Corazón"), song(1, "Corazón - Remastered 2007")))
        assertEquals(1L, find(ref("Corazón [Radio Edit]"), song(1, "Corazón (Radio Edit)")))
    }

    @Test
    fun aTitleThatOnlyContainsAnotherIsNotLookedUpAsIt() {
        assertNull(find(ref("Love"), song(1, "Love Story")))
    }

    @Test
    fun aRemixOrALiveVersionIsAnotherSong() {
        assertNull(find(ref("Zoom"), song(1, "Zoom (Remix)")))
        assertNull(find(ref("Zoom (Live)"), song(1, "Zoom")))
    }

    @Test
    fun anotherArtistWithTheSameTitleIsAnotherSong() {
        assertNull(find(ref("Hello", artist = "Adele"), song(1, "Hello", artist = "Lionel Richie")))
        assertEquals(2L, find(ref("Hello", artist = "Adele"), song(1, "Hello", artist = "Lionel Richie"), song(2, "Hello", artist = "Adele")))
    }

    @Test
    fun aCreditedArtistIsEnoughAndABlankOneIsNotHeldAgainstIt() {
        assertEquals(1L, find(ref("Duet", artist = "Gustavo Cerati"), song(1, "Duet", artist = "Soda Stereo, Gustavo Cerati")))
        assertEquals(1L, find(ref("Duet", artist = ""), song(1, "Duet")))
    }

    @Test
    fun theLengthMustBeCloseButAnUnknownOneIsNotAMismatch() {
        assertEquals(1L, find(ref("Song", durationMs = 210_000), song(1, "Song", durationMs = 214_000)))
        assertNull(find(ref("Song", durationMs = 210_000), song(1, "Song", durationMs = 260_000)))
        assertEquals(1L, find(ref("Song", durationMs = 0), song(1, "Song", durationMs = 260_000)))
    }

    @Test
    fun twoCopiesTakeTheSameAlbumThenTheClosestLengthAndAlwaysTheSame() {
        val library = arrayOf(
            song(1, "Song", album = "Compilation", durationMs = 210_000),
            song(2, "Song", album = "Canción Animal", durationMs = 215_000),
            song(3, "Song", album = "Canción Animal", durationMs = 211_000),
        )
        assertEquals(3L, find(ref("Song", album = "Canción Animal"), *library))
        assertEquals(1L, find(ref("Song", album = "Compilation"), *library))
        // No album saved: the closest length, then the lowest id, whatever order the library is in.
        assertEquals(1L, find(ref("Song", album = ""), *library))
        assertEquals(1L, find(ref("Song", album = ""), *library.reversedArray()))
    }

    @Test
    fun titlesMadeOfSymbolsMatchBySymbols() {
        assertEquals(1L, find(ref("÷"), song(1, "÷"), song(2, "★")))
        assertNull(find(ref("★"), song(1, "÷")))
    }

    @Test
    fun whatThePhoneDoesNotHaveIsNull() {
        assertNull(find(ref("Nunca la tuve"), song(1, "Otra")))
        assertNull(SongMatcher(emptyList()).find(ref("Cualquiera")))
    }
}
