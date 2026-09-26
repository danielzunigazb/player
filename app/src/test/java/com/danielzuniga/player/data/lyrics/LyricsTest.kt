package com.danielzuniga.player.data.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class LrcParserTest {

    @Test
    fun parsesTimestampsInAllCommonFormats() {
        val lyrics = LrcParser.parse(
            """
            [ar:Soda Stereo]
            [00:05.50]Primera
            [00:10]Segunda
            [01:02.123]Tercera
            [00:20:10]Cuarta
            """.trimIndent()
        ) as Lyrics.Synced

        assertEquals(listOf(5_500L, 10_000L, 20_100L, 62_123L), lyrics.lines.map { it.timeMs })
        assertEquals("Primera", lyrics.lines.first().text)
    }

    @Test
    fun repeatsLinesWithSeveralTimestampsAndAppliesOffset() {
        val lyrics = LrcParser.parse("[offset:+500]\n[00:10.00][00:30.00]Coro\n[00:20.00]Verso") as Lyrics.Synced
        assertEquals(listOf(9_500L, 19_500L, 29_500L), lyrics.lines.map { it.timeMs })
        assertEquals(listOf("Coro", "Verso", "Coro"), lyrics.lines.map { it.text })
    }

    @Test
    fun findsCurrentLine() {
        val lyrics = LrcParser.parse("[00:01]a\n[00:05]b\n[00:09]c") as Lyrics.Synced
        assertEquals(-1, lyrics.indexAt(500))
        assertEquals(0, lyrics.indexAt(1_000))
        assertEquals(1, lyrics.indexAt(8_999))
        assertEquals(2, lyrics.indexAt(60_000))
    }

    @Test
    fun plainTextWithoutTimestamps() {
        val lyrics = LrcParser.parse("[ti:Algo]\nLínea uno\r\nLínea dos\n")
        assertEquals(Lyrics.Plain("Línea uno\nLínea dos"), lyrics)
        assertNull(LrcParser.parse("   \n "))
    }
}

class EmbeddedLyricsTest {

    @Test
    fun readsId3v23UsltInUtf16() {
        val mp3 = id3(major = 3, frames = listOf(textFrame("TIT2", "Título"), uslt(encoding = 1, text = "Canción con ñ")))
        assertEquals("Canción con ñ", EmbeddedLyrics.read(mp3.inputStream()))
    }

    @Test
    fun readsId3v24UsltInUtf8AfterLargeFrame() {
        val art = ByteArray(300_000) { 7 }
        val mp3 = id3(major = 4, frames = listOf(frame("APIC", art, major = 4), uslt(encoding = 3, text = "[00:01.00]Hola", major = 4)))
        assertEquals("[00:01.00]Hola", EmbeddedLyrics.read(mp3.inputStream()))
    }

    @Test
    fun returnsNullWhenMp3HasNoLyrics() {
        val mp3 = id3(major = 3, frames = listOf(textFrame("TIT2", "Título")))
        assertNull(EmbeddedLyrics.read(mp3.inputStream()))
        assertNull(EmbeddedLyrics.read(byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 1, 2).inputStream()))
    }

    @Test
    fun readsFlacVorbisLyricsSkippingOtherBlocks() {
        val flac = ByteArrayOutputStream().apply {
            write("fLaC".toByteArray())
            flacBlock(type = 0, body = ByteArray(34), last = false) // STREAMINFO
            flacBlock(type = 4, body = vorbis("TITLE=Algo", "unsyncedlyrics=Sin sincronizar", "LYRICS=[00:02]Sí"), last = true)
        }.toByteArray()
        assertEquals("[00:02]Sí", EmbeddedLyrics.read(flac.inputStream()))
    }

    @Test
    fun truncatedFilesDoNotCrash() {
        val mp3 = id3(major = 3, frames = listOf(uslt(encoding = 0, text = "x")))
        assertNull(EmbeddedLyrics.read(mp3.copyOf(20).inputStream()))
        assertTrue(EmbeddedLyrics.read(ByteArray(0).inputStream()) == null)
    }

    // ---- builders

    private fun id3(major: Int, frames: List<ByteArray>): ByteArray {
        val body = frames.fold(ByteArray(0)) { acc, f -> acc + f } + ByteArray(64) // padding
        return byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), major.toByte(), 0, 0) +
            synchsafe(body.size) + body + byteArrayOf(0xFF.toByte(), 0xFB.toByte())
    }

    private fun frame(id: String, body: ByteArray, major: Int = 3): ByteArray {
        val size = if (major == 4) synchsafe(body.size) else ByteBuffer.allocate(4).putInt(body.size).array()
        return id.toByteArray(Charsets.ISO_8859_1) + size + byteArrayOf(0, 0) + body
    }

    private fun textFrame(id: String, text: String) = frame(id, byteArrayOf(3) + text.toByteArray())

    private fun uslt(encoding: Int, text: String, major: Int = 3): ByteArray {
        val (charset, terminator) = when (encoding) {
            1 -> Charsets.UTF_16 to byteArrayOf(0, 0)
            3 -> Charsets.UTF_8 to byteArrayOf(0)
            else -> Charsets.ISO_8859_1 to byteArrayOf(0)
        }
        val descriptor = "desc".toByteArray(charset) + terminator
        return frame("USLT", byteArrayOf(encoding.toByte()) + "spa".toByteArray() + descriptor + text.toByteArray(charset), major)
    }

    private fun synchsafe(value: Int) = byteArrayOf(
        (value shr 21 and 0x7F).toByte(),
        (value shr 14 and 0x7F).toByte(),
        (value shr 7 and 0x7F).toByte(),
        (value and 0x7F).toByte(),
    )

    private fun ByteArrayOutputStream.flacBlock(type: Int, body: ByteArray, last: Boolean) {
        write((if (last) 0x80 else 0) or type)
        write(body.size shr 16 and 0xFF)
        write(body.size shr 8 and 0xFF)
        write(body.size and 0xFF)
        write(body)
    }

    private fun vorbis(vararg comments: String): ByteArray {
        val out = ByteArrayOutputStream()
        fun le32(v: Int) = out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array())
        val vendor = "test".toByteArray()
        le32(vendor.size); out.write(vendor)
        le32(comments.size)
        comments.forEach { val b = it.toByteArray(); le32(b.size); out.write(b) }
        return out.toByteArray()
    }
}
