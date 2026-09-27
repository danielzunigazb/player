package com.danielzuniga.player.remote

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The monitor's encryption (docs/monitor.md): AES-256-GCM with the room's key, sent as
 * base64url(iv || ciphertext+tag). Byte for byte what site/monitor/protocol.js does; both check
 * the same test vector.
 */
object RemoteCrypto {

    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private val random = SecureRandom()
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    /** A new room: 16 random bytes, 22 characters. */
    fun newRoom(): String = encoder.encodeToString(randomBytes(16))

    /** A new key: 32 random bytes, 43 characters. */
    fun newKey(): String = encoder.encodeToString(randomBytes(32))

    fun isRoom(text: String) = ROOM.matches(text)
    fun isKey(text: String) = KEY.matches(text)

    /**
     * A new pairing code: what the phone shows and the person types in the monitor. 6 characters
     * from an alphabet without look-alikes (no 0/O, 1/I/L): about 30 bits, with 3 tries.
     */
    fun newPairingCode(): String =
        (1..PAIRING_CODE_LENGTH).map { PAIRING_ALPHABET[random.nextInt(PAIRING_ALPHABET.length)] }.joinToString("")

    /** The same code however it was typed: uppercase, without spaces or dashes. */
    fun normalizeCode(text: String): String = text.uppercase().filterNot { it.isWhitespace() || it == '-' }

    /** Whether [typed] is [code], in time that doesn't depend on where they differ. */
    fun codeMatches(code: String, typed: String): Boolean =
        MessageDigest.isEqual(code.toByteArray(), normalizeCode(typed).toByteArray())

    fun seal(key: String, plaintext: String, iv: ByteArray = randomBytes(IV_BYTES)): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secret(key), GCMParameterSpec(TAG_BITS, iv))
        return encoder.encodeToString(iv + cipher.doFinal(plaintext.toByteArray()))
    }

    /** The plaintext inside [text], or null when it isn't ours (other key, altered, garbage). */
    fun open(key: String, text: String): String? {
        val bytes = try {
            decoder.decode(text)
        } catch (e: IllegalArgumentException) {
            return null
        }
        if (bytes.size < IV_BYTES + TAG_BITS / 8) return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secret(key), GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
        return try {
            String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES))
        } catch (e: AEADBadTagException) {
            null
        }
    }

    private fun secret(key: String): SecretKeySpec {
        val raw = decoder.decode(key)
        require(raw.size == 32) { "bad key" }
        return SecretKeySpec(raw, "AES")
    }

    private fun randomBytes(n: Int) = ByteArray(n).also(random::nextBytes)

    private val ROOM = Regex("[A-Za-z0-9_-]{22}")
    private val KEY = Regex("[A-Za-z0-9_-]{43}")
    const val PAIRING_CODE_LENGTH = 6
    const val PAIRING_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
}
