package com.danielzuniga.player.data.backup

import com.danielzuniga.player.data.Song
import com.danielzuniga.player.data.backup.PlaylistM3u.Entry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistM3uTest {

    private fun song(title: String, artist: String = "Soda Stereo", durationMs: Long = 210_000, path: String = "") =
        Song(1, title, artist, "Álbum", 1, durationMs, path = path)

    private fun refs(entry: Entry) = PlaylistM3u.candidates(entry).map { it.artist to it.title }

    @Test
    fun writesWhatOtherPlayersRead() {
        val text = PlaylistM3u.format(
            "Rock",
            listOf(
                song("De Música Ligera", path = "/storage/emulated/0/Music/ligera.mp3"),
                song("Instrumental", artist = "", durationMs = 61_500, path = "/storage/emulated/0/Music/x.flac"),
            ),
        )
        assertEquals(
            """
            #EXTM3U
            #PLAYLIST:Rock
            #EXTINF:210,Soda Stereo - De Música Ligera
            /storage/emulated/0/Music/ligera.mp3
            #EXTINF:61,Instrumental
            /storage/emulated/0/Music/x.flac
            """.trimIndent() + "\n",
            text,
        )
    }

    @Test
    fun aSongWithoutAPathIsStillNamedAndNewlinesCantBreakALine() {
        val text = PlaylistM3u.format("Una\nlista", listOf(song("Dos\r\nlíneas", path = "")))
        assertEquals(
            listOf("#EXTM3U", "#PLAYLIST:Una lista", "#EXTINF:210,Soda Stereo - Dos líneas", "Soda Stereo - Dos líneas"),
            text.trimEnd().lines(),
        )
    }

    @Test
    fun whatIsWrittenIsReadBack() {
        val songs = listOf(song("Persiana Americana", durationMs = 280_000, path = "/m/p.mp3"), song("Zoom", path = "/m/z.mp3"))
        val parsed = PlaylistM3u.parse(PlaylistM3u.format("Mix", songs))

        assertEquals("Mix", parsed.name)
        assertEquals(
            listOf(
                Entry(280_000, "Soda Stereo - Persiana Americana", "/m/p.mp3"),
                Entry(210_000, "Soda Stereo - Zoom", "/m/z.mp3"),
            ),
            parsed.entries,
        )
    }

    @Test
    fun readsWindowsLineEndingsAByteOrderMarkBlankLinesAndOtherComments() {
        val text = "\uFEFF#EXTM3U\r\n\r\n# a comment\r\n#EXTALB:Signos\r\n#EXTINF:280,Soda Stereo - Persiana Americana\r\nC:\\Music\\p.mp3\r\n\r\n"
        val parsed = PlaylistM3u.parse(text)

        assertNull(parsed.name)
        assertEquals(listOf(Entry(280_000, "Soda Stereo - Persiana Americana", "C:\\Music\\p.mp3")), parsed.entries)
    }

    @Test
    fun readsExtinfWithAttributesAndUnknownOrZeroLengths() {
        val parsed = PlaylistM3u.parse(
            """
            #EXTM3U
            #EXTINF:-1 tvg-id="x" group-title="y",Radio Uno
            http://example.com/stream
            #EXTINF:0,Sin duración
            /m/a.mp3
            #EXTINF:abc,Duración rara
            /m/b.mp3
            #EXTINF:12.5,Con decimales
            /m/c.mp3
            """.trimIndent(),
        )
        assertEquals(listOf(0L, 0L, 0L, 12_500L), parsed.entries.map { it.durationMs })
        assertEquals("Radio Uno", parsed.entries.first().text)
    }

    @Test
    fun entriesWithNoExtinfAndExtinfsWithNoPathStillCount() {
        val parsed = PlaylistM3u.parse(
            """
            /music/Soda Stereo - Zoom.mp3
            #EXTINF:200,Solo texto A
            #EXTINF:210,Solo texto B
            /music/b.mp3
            #EXTINF:220,Solo texto al final
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                Entry(0, null, "/music/Soda Stereo - Zoom.mp3"),
                Entry(200_000, "Solo texto A", null),
                Entry(210_000, "Solo texto B", "/music/b.mp3"),
                Entry(220_000, "Solo texto al final", null),
            ),
            parsed.entries,
        )
    }

    @Test
    fun theNameComesFromPlaylistWhenThereIsOne() {
        assertEquals("Mi lista", PlaylistM3u.parse("#EXTM3U\n#PLAYLIST: Mi lista \n/a.mp3").name)
        assertNull(PlaylistM3u.parse("#PLAYLIST:\n/a.mp3").name)
    }

    @Test
    fun aTextWithoutSongsHasNoEntries() {
        assertTrue(PlaylistM3u.parse("").entries.isEmpty())
        assertTrue(PlaylistM3u.parse("#EXTM3U\n# nada\n").entries.isEmpty())
    }

    @Test
    fun theTextIsReadAsArtistAndTitleAndAlsoAsAWholeTitle() {
        assertEquals(
            listOf("Soda Stereo" to "Zoom - Live", "" to "Soda Stereo - Zoom - Live"),
            refs(Entry(0, "Soda Stereo - Zoom - Live", null)),
        )
        assertEquals(listOf("" to "Sin artista"), refs(Entry(0, "Sin artista", null)))
    }

    @Test
    fun aFileNameLosesItsFoldersItsExtensionAndALeadingTrackNumber() {
        val unix = Entry(0, null, "/storage/0/Music/Soda Stereo/03 - Soda Stereo - Zoom.mp3")
        assertEquals("Soda Stereo" to "Zoom", refs(unix).first())
        val windows = Entry(0, null, "D:\\Música\\07. Persiana Americana.flac")
        assertEquals("" to "Persiana Americana", refs(windows).first())
        // The name as it was is tried too: a song can really be called "7 - Seven".
        assertTrue(("7" to "Seven") in refs(Entry(0, null, "/m/7 - Seven.mp3")))
    }

    @Test
    fun aUrlFileNameIsDecoded() {
        assertEquals(
            "Soda Stereo" to "Canción",
            refs(Entry(0, null, "file:///sdcard/Soda%20Stereo%20-%20Canci%C3%B3n.mp3")).first(),
        )
    }

    @Test
    fun anEntryWithNothingToGoByHasNoCandidatesAndTheLengthRidesAlong() {
        assertTrue(PlaylistM3u.candidates(Entry(0, null, null)).isEmpty())
        assertTrue(PlaylistM3u.candidates(Entry(0, "   ", "   ")).isEmpty())
        assertEquals(240_000L, PlaylistM3u.candidates(Entry(240_000, "A - B", null)).first().durationMs)
    }

    @Test
    fun namesFromFilesAndFilesFromNames() {
        assertEquals("Mis favoritas", PlaylistM3u.nameFromFile("Mis favoritas.m3u8"))
        assertEquals("Con.puntos", PlaylistM3u.nameFromFile("Con.puntos.m3u"))
        assertEquals("sin extensión", PlaylistM3u.nameFromFile("sin extensión"))
        assertEquals("Rock_ Pop_ _1.m3u8", PlaylistM3u.fileNameFor("Rock/ Pop? \"1"))
        assertEquals("playlist.m3u8", PlaylistM3u.fileNameFor("  "))
    }
}
