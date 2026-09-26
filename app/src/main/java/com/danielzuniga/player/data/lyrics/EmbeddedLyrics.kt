package com.danielzuniga.player.data.lyrics

import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.nio.charset.Charset

/**
 * Reads lyrics stored inside audio files: ID3v2.3/2.4 `USLT` frames (MP3) and
 * Vorbis comments `LYRICS` / `UNSYNCEDLYRICS` (FLAC). Returns null when there are none.
 */
object EmbeddedLyrics {

    private const val MAX_TAG_BYTES = 16 * 1024 * 1024

    fun read(input: InputStream): String? = try {
        val data = DataInputStream(input.buffered())
        val magic = ByteArray(4)
        data.readFully(magic, 0, 3)
        when {
            magic.startsWith("ID3") -> readId3(data)
            else -> {
                data.readFully(magic, 3, 1)
                if (magic.startsWith("fLaC")) readFlac(data) else null
            }
        }
    } catch (_: IOException) {
        null
    }

    // ---------------------------------------------------------------- ID3v2

    private fun readId3(data: DataInputStream): String? {
        val major = data.readUnsignedByte()
        data.readUnsignedByte() // revision
        val flags = data.readUnsignedByte()
        val tagSize = synchsafe(data.readInt())
        if (major !in 3..4 || tagSize <= 0 || tagSize > MAX_TAG_BYTES) return null

        val tag = ByteArray(tagSize)
        data.readFully(tag)
        var pos = 0
        if (flags and 0x40 != 0) {
            val extSize = int32(tag, 0)
            pos = if (major == 4) synchsafe(extSize) else extSize + 4
        }

        while (pos + 10 <= tag.size) {
            val id = String(tag, pos, 4, Charsets.ISO_8859_1)
            if (id[0] == '\u0000') break
            val rawSize = int32(tag, pos + 4)
            val size = if (major == 4) synchsafe(rawSize) else rawSize
            val bodyStart = pos + 10
            if (size <= 0 || bodyStart + size > tag.size) break
            if (id == "USLT") {
                decodeUslt(tag, bodyStart, size)?.let { return it }
            }
            pos = bodyStart + size
        }
        return null
    }

    private fun decodeUslt(tag: ByteArray, start: Int, size: Int): String? {
        if (size < 5) return null
        val encoding = tag[start].toInt()
        val charset = id3Charset(encoding)
        val wide = encoding == 1 || encoding == 2
        // Skip encoding (1) + language (3), then the null-terminated content descriptor.
        var pos = start + 4
        val end = start + size
        pos = skipTerminated(tag, pos, end, wide)
        if (pos >= end) return null
        return String(tag, pos, end - pos, charset).trimEnd('\u0000').trim().ifEmpty { null }
    }

    private fun skipTerminated(bytes: ByteArray, from: Int, end: Int, wide: Boolean): Int {
        var i = from
        if (wide) {
            while (i + 1 < end) {
                if (bytes[i].toInt() == 0 && bytes[i + 1].toInt() == 0) return i + 2
                i += 2
            }
        } else {
            while (i < end) {
                if (bytes[i].toInt() == 0) return i + 1
                i++
            }
        }
        return end
    }

    private fun id3Charset(encoding: Int): Charset = when (encoding) {
        1 -> Charsets.UTF_16
        2 -> Charsets.UTF_16BE
        3 -> Charsets.UTF_8
        else -> Charsets.ISO_8859_1
    }

    private fun synchsafe(value: Int): Int =
        (value and 0x7F) or ((value shr 8 and 0x7F) shl 7) or
            ((value shr 16 and 0x7F) shl 14) or ((value shr 24 and 0x7F) shl 21)

    private fun int32(bytes: ByteArray, at: Int): Int =
        (bytes[at].toInt() and 0xFF shl 24) or (bytes[at + 1].toInt() and 0xFF shl 16) or
            (bytes[at + 2].toInt() and 0xFF shl 8) or (bytes[at + 3].toInt() and 0xFF)

    // ---------------------------------------------------------------- FLAC

    private fun readFlac(data: DataInputStream): String? {
        while (true) {
            val header = data.readUnsignedByte()
            val isLast = header and 0x80 != 0
            val type = header and 0x7F
            val length = (data.readUnsignedByte() shl 16) or (data.readUnsignedByte() shl 8) or data.readUnsignedByte()
            if (type == VORBIS_COMMENT) {
                if (length > MAX_TAG_BYTES) return null
                val block = ByteArray(length)
                data.readFully(block)
                return vorbisLyrics(block)
            }
            skipFully(data, length)
            if (isLast) return null
        }
    }

    private fun vorbisLyrics(block: ByteArray): String? {
        var pos = 0
        fun le32(): Int {
            if (pos + 4 > block.size) throw EOFException()
            val v = (block[pos].toInt() and 0xFF) or (block[pos + 1].toInt() and 0xFF shl 8) or
                (block[pos + 2].toInt() and 0xFF shl 16) or (block[pos + 3].toInt() and 0xFF shl 24)
            pos += 4
            return v
        }
        return try {
            val vendorLength = le32()
            pos += vendorLength
            val count = le32()
            var unsynced: String? = null
            repeat(count) {
                val len = le32()
                if (len < 0 || pos + len > block.size) return null
                val comment = String(block, pos, len, Charsets.UTF_8)
                pos += len
                val key = comment.substringBefore('=').uppercase()
                val value = comment.substringAfter('=', "").trim()
                if (value.isNotEmpty()) {
                    if (key == "LYRICS") return value
                    if (key == "UNSYNCEDLYRICS") unsynced = value
                }
            }
            unsynced
        } catch (_: EOFException) {
            null
        }
    }

    private fun skipFully(data: DataInputStream, count: Int) {
        var remaining = count
        while (remaining > 0) {
            val skipped = data.skipBytes(remaining)
            if (skipped <= 0) throw EOFException()
            remaining -= skipped
        }
    }

    private fun ByteArray.startsWith(prefix: String): Boolean =
        prefix.indices.all { this[it] == prefix[it].code.toByte() }

    private const val VORBIS_COMMENT = 4
}
