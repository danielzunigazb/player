package com.danielzuniga.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackMatchTest {

    @Test
    fun titlesIgnoreAccentsPunctuationAndDecorations() {
        assertTrue(TrackMatch.trackTitlesMatch("De Música Ligera", "De musica ligera"))
        assertTrue(TrackMatch.trackTitlesMatch("Persiana Americana (Remastered 2007)", "Persiana Americana"))
        assertTrue(TrackMatch.trackTitlesMatch("Eres (feat. Alguien)", "Eres"))
        assertTrue(TrackMatch.trackTitlesMatch("Rock & Roll", "Rock and Roll"))
        assertTrue(TrackMatch.trackTitlesMatch("Hip-Hop Hooray", "Hip Hop Hooray"))
    }

    @Test
    fun versionWordsKeepRecordingsApart() {
        assertFalse(TrackMatch.trackTitlesMatch("De Música Ligera", "De Música Ligera (Remix)"))
        assertFalse(TrackMatch.trackTitlesMatch("Eres", "Eres (Live)"))
        assertFalse(TrackMatch.trackTitlesMatch("Clandestino", "Clandestino (Acoustic)"))
        // Different songs never match.
        assertFalse(TrackMatch.trackTitlesMatch("De Música Ligera", "Lamento Boliviano"))
    }

    @Test
    fun artistsMatchWhenAnyCreditIsShared() {
        assertTrue(TrackMatch.artistNamesMatch("Soda Stereo", "soda stereo"))
        assertTrue(TrackMatch.artistNamesMatch("Rosalía feat. J Balvin", "J Balvin"))
        assertTrue(TrackMatch.artistNamesMatch("Natanael Cano, Tito Double P", "Tito Double P & Peso Pluma"))
        assertTrue(TrackMatch.artistNamesMatch("Café Tacvba", "Cafe Tacvba"))
        assertTrue(TrackMatch.artistNamesMatch("Cano Natanael", "Natanael Cano"))
        assertFalse(TrackMatch.artistNamesMatch("Soda Stereo", "Enanitos Verdes"))
    }

    @Test
    fun singleWordContainmentIsNotEnough() {
        // "rock" is inside "one ok rock" but is not the same artist.
        assertFalse(TrackMatch.artistNamesMatch("Rock", "One Ok Rock"))
        assertTrue(TrackMatch.artistNamesMatch("Gustavo Cerati", "Gustavo Cerati y Soda Stereo"))
    }

    @Test
    fun durationsWithinTenSeconds() {
        assertTrue(TrackMatch.durationMatches(211_000, 219_000))
        assertFalse(TrackMatch.durationMatches(211_000, 260_000))
        // Unknown lengths never veto a match.
        assertTrue(TrackMatch.durationMatches(0, 260_000))
    }

    @Test
    fun normalisesSpecialLetters() {
        assertEquals("strasse", TrackMatch.normalizeSearchText("Straße"))
        assertEquals("aeon", TrackMatch.normalizeSearchText("Æon"))
        assertEquals("cancion", TrackMatch.normalizeSearchText("¡Canción!"))
    }

    @Test
    fun sameTrackCombinesAllChecks() {
        assertTrue(TrackMatch.sameTrack("Eres", "Café Tacvba", 265_000, "Eres", "Cafe Tacvba", 266_000))
        assertFalse(TrackMatch.sameTrack("Eres", "Café Tacvba", 265_000, "Eres", "Café Tacvba", 320_000))
    }
}
