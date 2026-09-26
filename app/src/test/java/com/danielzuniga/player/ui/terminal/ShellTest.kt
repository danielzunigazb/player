package com.danielzuniga.player.ui.terminal

import androidx.media3.common.Player
import com.danielzuniga.player.data.LibraryIndex
import com.danielzuniga.player.data.Song
import com.danielzuniga.player.playback.NowPlaying
import com.danielzuniga.player.playback.PlayerUiState
import com.danielzuniga.player.playback.SessionCommands
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellTest {

    private val songs = listOf(
        Song(1, "De Música Ligera", "Soda Stereo", "Canción Animal", 10, 210_000, track = 1),
        Song(2, "Un Millón de Años Luz", "Soda Stereo", "Canción Animal", 10, 280_000, track = 2),
        Song(3, "Persiana Americana", "Soda Stereo", "Signos", 11, 290_000, track = 1),
        Song(4, "Eres", "Café Tacvba", "Cuatro Caminos", 20, 265_000, track = 1),
        Song(5, "Signos", "Soda Stereo", "Signos", 11, 300_000, track = 2),
    )

    private class FakeHost(override val library: LibraryIndex) : ShellHost {
        override var player = PlayerUiState()
        override var favoriteIds = emptySet<Long>()
        override var mostPlayed = emptyList<Song>()
        override var currentLyric: String? = null
        val calls = mutableListOf<String>()
        var lastSongs: List<Song> = emptyList()

        override fun play(songs: List<Song>, index: Int) { calls += "play"; lastSongs = songs }
        override fun shuffle(songs: List<Song>) { calls += "shuffle"; lastSongs = songs }
        override fun playNext(songs: List<Song>) { calls += "playNext"; lastSongs = songs }
        override fun addToQueue(songs: List<Song>) { calls += "queue"; lastSongs = songs }
        override fun togglePlay() { calls += "toggle" }
        override fun skip() { calls += "skip" }
        override fun previous() { calls += "prev" }
        override fun seek(positionMs: Long) { calls += "seek:$positionMs" }
        override fun toggleShuffle() { calls += "random" }
        override fun cycleRepeat() { calls += "repeat" }
        override fun toggleFavorite(songId: Long) { calls += "fav:$songId" }
        override fun setSleep(minutes: Int) { calls += "sleep:$minutes" }
        override fun setSpeed(speed: Float) { calls += "speed:$speed" }
        override fun share(lines: List<String>) { calls += "share:${lines.joinToString("|")}" }
    }

    private val host = FakeHost(LibraryIndex(songs))
    private val shell = Shell(host, ShellText.Spanish)

    private fun run(input: String) = shell.run(input).drop(1) // drop the echoed input

    @Test
    fun playResolvesArtistsAlbumsAndSongsWithoutAccents() {
        assertEquals(LineKind.OK, run("play soda stereo").single().kind)
        assertEquals(listOf(1L, 2L, 3L, 5L), host.lastSongs.map { it.id }.sorted())

        run("play cafe tacvba")
        assertEquals(listOf(4L), host.lastSongs.map { it.id })

        run("play musica ligera")
        assertEquals(listOf(1L), host.lastSongs.map { it.id })
    }

    @Test
    fun kindPrefixDisambiguatesAlbumFromSong() {
        // "Signos" is both an album and a song: the album wins by default…
        run("play signos")
        assertEquals(listOf(3L, 5L), host.lastSongs.map { it.id })
        // …and `song` asks for the track.
        run("play song signos")
        assertEquals(listOf(5L), host.lastSongs.map { it.id })
    }

    @Test
    fun queueNextAndErrors() {
        run("queue eres")
        assertEquals("queue", host.calls.last())
        run("next persiana")
        assertEquals("playNext", host.calls.last())
        run("next")
        assertEquals("skip", host.calls.last())

        assertEquals(LineKind.ERR, run("play zzz").single().kind)
        assertEquals(LineKind.ERR, run("queue").single().kind)
        val unknown = run("dance").single()
        assertEquals(LineKind.ERR, unknown.kind)
        assertTrue(unknown.text.startsWith("dance: comando no encontrado"))
    }

    @Test
    fun sleepSpeedAndSeekParseArguments() {
        run("sleep 30m"); assertEquals("sleep:30", host.calls.last())
        run("sleep 1h"); assertEquals("sleep:60", host.calls.last())
        run("sleep end"); assertEquals("sleep:${SessionCommands.SLEEP_END_OF_TRACK}", host.calls.last())
        run("sleep off"); assertEquals("sleep:${SessionCommands.SLEEP_OFF}", host.calls.last())
        assertEquals(LineKind.ERR, run("sleep mañana").single().kind)

        run("speed 1.25x"); assertEquals("speed:1.25", host.calls.last())
        assertEquals(LineKind.ERR, run("speed 5").single().kind)

        host.player = PlayerUiState(nowPlaying = NowPlaying(1, "De Música Ligera", "Soda Stereo", null), positionMs = 60_000, durationMs = 210_000)
        run("seek 1:30"); assertEquals("seek:90000", host.calls.last())
        run("seek +15"); assertEquals("seek:75000", host.calls.last())
        run("seek 9:00"); assertEquals("seek:210000", host.calls.last())
    }

    @Test
    fun nowShowsProgressBarFlagsAndLyric() {
        host.player = PlayerUiState(
            nowPlaying = NowPlaying(1, "De Música Ligera", "Soda Stereo", null),
            isPlaying = true, positionMs = 105_000, durationMs = 210_000,
            repeatMode = Player.REPEAT_MODE_ONE,
        )
        host.favoriteIds = setOf(1L)
        host.currentLyric = "De música ligera"
        val out = run("now").map { it.text }
        assertEquals("De Música Ligera", out[0])
        assertEquals("[██████████░░░░░░░░░░] 1:45 / 3:30", out[2])
        assertEquals("▶ · repite una · ♥", out[3])
        assertEquals("♪ De música ligera", out[4])
    }

    @Test
    fun suggestsCommandsThenLibraryMatches() {
        // Commands that take free text complete with a trailing space, ready for the query.
        assertEquals(setOf("shuffle ", "seek", "sleep", "speed", "share"), shell.suggest("s").toSet())
        assertEquals(listOf("play soda stereo"), shell.suggest("play sod").take(1))
        assertTrue(shell.suggest("queue er").contains("queue eres"))
        assertTrue(shell.suggest("sleep 3").isEmpty())
    }

    @Test
    fun easterEggsAndUiActions() {
        assertEquals("daniel zúñiga", run("whoami").single().text)
        val sudo = run("sudo play eres")
        assertEquals("no hace falta root aquí, brother.", sudo.first().text)
        assertEquals("play", host.calls.last())
        assertEquals(Shell.CLEAR, shell.uiAction("clear"))
        assertEquals(Shell.EXIT, shell.uiAction("quit"))
        assertEquals(null, shell.uiAction("play eres"))
    }

    @Test
    fun sharesTheSongOrTheLyricBeingSung() {
        assertEquals("nada sonando.", run("share").single().text)

        host.player = PlayerUiState(nowPlaying = NowPlaying(1, "De Música Ligera", "Soda Stereo", null), isPlaying = true)
        run("share")
        assertEquals("share:", host.calls.last())

        assertEquals("no hay una línea de letra sonando ahora.", run("share lyric").single().text)
        host.currentLyric = "Ella durmió al calor de las masas"
        run("share lyric")
        assertEquals("share:Ella durmió al calor de las masas", host.calls.last())
        assertEquals(LineKind.ERR, run("share todo").single().kind)
    }

    @Test
    fun everyCommandIsDescribedInEveryLanguage() {
        listOf(ShellText.Spanish, ShellText.English).forEach { text ->
            shell.commandNames.forEach { name ->
                assertTrue("$name has no description in ${text::class.simpleName}", text.help(name).isNotBlank())
            }
        }
    }

    @Test
    fun speaksEnglish() {
        val english = Shell(host, ShellText.English)
        val drop = { input: String -> english.run(input).drop(1) }
        assertEquals("▶ Soda Stereo · 4 songs", drop("play soda stereo").single().text)
        assertEquals("+ queued: Eres — Café Tacvba", drop("queue eres").single().text)
        assertEquals("dance: command not found. try `help`.", drop("dance").single().text)
        assertEquals("usage: queue <artist|album|song>", drop("queue").single().text)
        assertEquals("player · 5 songs · 3 albums · 2 artists", english.banner().first().text)
        assertEquals(ShellText.English, ShellText.forLanguage("en"))
        assertEquals(ShellText.English, ShellText.forLanguage("fr"))
        assertEquals(ShellText.Spanish, ShellText.forLanguage("es"))
    }

    @Test
    fun parsesSeekFormatsAndDrawsBars() {
        assertEquals(90_000L, Shell.parseSeek("1:30", 0))
        assertEquals(null, Shell.parseSeek("1:75", 0))
        assertEquals(20_000L, Shell.parseSeek("-10", 30_000))
        assertEquals("[░░░░░░░░░░░░░░░░░░░░]", Shell.progressBar(0f))
        assertEquals("[████████████████████]", Shell.progressBar(1f))
    }
}
