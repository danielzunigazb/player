package com.danielzuniga.player.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class RemoteCryptoTest {

    // Same vector as site/monitor/protocol.test.js: the monitor and the app must agree byte for byte.
    private val key = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8"
    private val iv = Base64.getUrlDecoder().decode("oKGio6Slpqeoqaqr")
    private val message = """{"type":"cmd","op":"next","from":"web-1","seq":1,"ts":1790000000000}"""
    private val sealed = "oKGio6SlpqeoqaqrnToIVDWuIIVABuq3JVbisQCOYzL80joYviIE4A3EGCPoVDCazQ9iH3O-d614WLnIazkyO0DqK0l4bjtulEC1gISMtRKaCxcv0Ky-OrHIXDAjrcnA"

    @Test
    fun sealsExactlyLikeTheMonitor() {
        assertEquals(sealed, RemoteCrypto.seal(key, message, iv))
    }

    @Test
    fun opensWhatTheMonitorSealed() {
        assertEquals(message, RemoteCrypto.open(key, sealed))
    }

    @Test
    fun pairingCodesAreSixCharactersWithoutLookAlikes() {
        val codes = List(500) { RemoteCrypto.newPairingCode() }
        assertTrue(codes.all { it.length == 6 && it.all { c -> c in RemoteCrypto.PAIRING_ALPHABET } })
        assertTrue(codes.none { code -> code.any { it in "0O1IL" } })
        assertEquals(31, RemoteCrypto.PAIRING_ALPHABET.toSet().size)
        // Random, and the whole alphabet shows up.
        assertTrue(codes.toSet().size > 490)
        assertEquals(RemoteCrypto.PAIRING_ALPHABET.toSet(), codes.joinToString("").toSet())
    }

    @Test
    fun aTypedCodeMatchesHoweverItWasWritten() {
        assertTrue(RemoteCrypto.codeMatches("AB3K9Z", "ab3 k9z"))
        assertTrue(RemoteCrypto.codeMatches("AB3K9Z", "AB3-K9Z"))
        assertTrue(!RemoteCrypto.codeMatches("AB3K9Z", "AB3K9Y"))
        assertTrue(!RemoteCrypto.codeMatches("AB3K9Z", "AB3K9"))
        assertTrue(!RemoteCrypto.codeMatches("AB3K9Z", ""))
    }

    @Test
    fun roundTripsWithARandomIv() {
        val k = RemoteCrypto.newKey()
        val a = RemoteCrypto.seal(k, "hola")
        assertNotEquals(a, RemoteCrypto.seal(k, "hola"))
        assertEquals("hola", RemoteCrypto.open(k, a))
    }

    @Test
    fun anotherKeyAlteredOrGarbageDoesNotOpen() {
        assertNull(RemoteCrypto.open(RemoteCrypto.newKey(), sealed))
        val flipped = sealed.substring(0, 20) + (if (sealed[20] == 'A') 'B' else 'A') + sealed.substring(21)
        assertNull(RemoteCrypto.open(key, flipped))
        assertNull(RemoteCrypto.open(key, "not base64 at all!"))
        assertNull(RemoteCrypto.open(key, ""))
    }

    @Test
    fun roomsAndKeysHaveTheExpectedShape() {
        assertTrue(RemoteCrypto.isRoom(RemoteCrypto.newRoom()))
        assertTrue(RemoteCrypto.isKey(RemoteCrypto.newKey()))
        assertTrue(!RemoteCrypto.isRoom("short") && !RemoteCrypto.isKey(RemoteCrypto.newRoom()))
    }
}
