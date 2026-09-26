package com.danielzuniga.player.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TagTextTest {

    @Test
    fun splitsMessyArtistListsFromTheWild() {
        // The tag behind "Natanael Cano, ,, Tito Double P, Victor Mendivil, ..."
        assertEquals(
            listOf("Natanael Cano", "Tito Double P", "Victor Mendivil", "Peso Pluma"),
            TagText.artists("Natanael Cano, ,, Tito Double P, Victor Mendivil,, Peso Pluma ,"),
        )
        // ID3v2.4 separates multiple values with NUL.
        assertEquals(listOf("Bad Bunny", "Jhay Cortez"), TagText.artists("Bad Bunny\u0000Jhay Cortez"))
        assertEquals(listOf("Rosalía", "J Balvin"), TagText.artists("Rosalía feat. J Balvin"))
        assertEquals(listOf("Shakira", "Bizarrap"), TagText.artists("Shakira; Bizarrap"))
    }

    @Test
    fun keepsNamesThatOnlyLookLikeLists() {
        assertEquals(listOf("AC/DC"), TagText.artists("AC/DC"))
        assertEquals(listOf("Simon & Garfunkel"), TagText.artists("Simon & Garfunkel"))
        assertEquals(listOf("Café Tacvba"), TagText.artists("  Café   Tacvba "))
    }

    @Test
    fun dropsBlanksAndRepeatsIgnoringCaseAndAccents() {
        assertEquals(listOf("Soda Stereo"), TagText.artists("Soda Stereo, soda stereo, SODA STEREO"))
        assertEquals(listOf("Rosalía"), TagText.artists("Rosalía, Rosalia"))
        assertEquals(emptyList<String>(), TagText.artists(" , ,, "))
        assertEquals(emptyList<String>(), TagText.artists(null))
    }

    @Test
    fun cleansInvisibleCharactersAndWhitespace() {
        assertEquals("De Música Ligera", TagText.clean("﻿De​ Música\tLigera\n"))
        // NFD (decomposed) accents become the single composed character.
        assertEquals("Canción", TagText.clean("Canción"))
    }

    @Test
    fun calmsDownShoutedTitlesButKeepsStylisation() {
        assertEquals("Olivia La Flaka", TagText.title("OLIVIA LA FLAKA"))
        assertEquals("Rocky II (Remix)", TagText.title("ROCKY II (REMIX)"))
        assertEquals("Hip-Hop 4 Life", TagText.title("HIP-HOP 4 LIFE"))
        // One word or mixed case: left as the artist wrote it.
        assertEquals("HUMBLE.", TagText.title("HUMBLE."))
        assertEquals("deadmau5", TagText.title("deadmau5"))
        assertEquals("Persiana Americana", TagText.title("Persiana Americana"))
    }

    @Test
    fun shortCreditCountsTheRest() {
        val names = listOf("Natanael Cano", "Tito Double P", "Victor Mendivil", "Peso Pluma")
        assertEquals("Natanael Cano, Tito Double P +2", TagText.shortCredit(names))
        assertEquals("Soda Stereo", TagText.shortCredit(listOf("Soda Stereo")))
        // For wrapping text, names stay whole: only the ", " between artists can break.
        assertEquals("Natanael\u00A0Cano, Tito\u00A0Double\u00A0P\u00A0+2", TagText.shortCredit(names, keepNamesWhole = true))
    }
}
