package com.danielzuniga.player.data.tags

import com.danielzuniga.player.data.tags.TitleSplit.Reading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TitleSplitTest {

    @Test
    fun cleansDownloadNoise() {
        assertEquals("24K - T3R Elemento", TitleSplit.clean("01. 24K - T3R Elemento (Official Video) [320kbps]"))
        assertEquals("Eres - Café Tacvba", TitleSplit.clean("Eres_-_Café_Tacvba (Letra)"))
        assertEquals("Ella Durmió - Café Tacvba", TitleSplit.clean("03 - Ella Durmió - Café Tacvba [HD]"))
        // Brackets that belong to the song stay; so do numbers that are the title.
        assertEquals("Persiana Americana (Remix)", TitleSplit.clean("Persiana Americana (Remix)"))
        assertEquals("Shadow (Live)", TitleSplit.clean("Shadow (Live)"))
        assertEquals("1-800-273-8255", TitleSplit.clean("1-800-273-8255"))
        assertEquals("24K", TitleSplit.clean("24K"))
    }

    @Test
    fun offersBothReadingsOfADash() {
        assertEquals(
            listOf(Reading("24K", "T3R Elemento"), Reading("T3R Elemento", "24K")),
            TitleSplit.readings("24K - T3R Elemento (Audio Oficial)"),
        )
        assertEquals(
            listOf(Reading("Café Tacvba", "Eres"), Reading("Eres", "Café Tacvba")),
            TitleSplit.readings("Café Tacvba – Eres"),
        )
        // No separator, no guess.
        assertEquals(emptyList<Reading>(), TitleSplit.readings("t3r elemento 24k"))
        assertEquals(emptyList<Reading>(), TitleSplit.readings("Hip-Hop"))
    }

    @Test
    fun dropsAnArtistPrefixOnlyWhenItIsTheArtist() {
        assertEquals("De Música Ligera", TitleSplit.withoutArtistPrefix("Soda Stereo - De Música Ligera", "Soda Stereo"))
        assertEquals("De Música Ligera", TitleSplit.withoutArtistPrefix("soda stereo - De Música Ligera", "Soda Stereo"))
        assertNull(TitleSplit.withoutArtistPrefix("Signos - En Vivo", "Soda Stereo"))
        assertNull(TitleSplit.withoutArtistPrefix("De Música Ligera", "Soda Stereo"))
    }
}
