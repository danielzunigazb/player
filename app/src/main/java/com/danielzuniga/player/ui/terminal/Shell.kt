package com.danielzuniga.player.ui.terminal

import androidx.media3.common.Player
import com.danielzuniga.player.data.Album
import com.danielzuniga.player.data.Artist
import com.danielzuniga.player.data.LibraryIndex
import com.danielzuniga.player.data.Song
import com.danielzuniga.player.data.normalizedForSearch
import com.danielzuniga.player.playback.PlayerUiState
import com.danielzuniga.player.playback.SessionCommands
import com.danielzuniga.player.ui.formatDuration
import com.danielzuniga.player.ui.player.formatSpeed
import com.danielzuniga.player.ui.player.progress

enum class LineKind { INPUT, OUT, OK, ERR, DIM }

data class ShellLine(val text: String, val kind: LineKind = LineKind.OUT)

/** Everything the shell may read or do; the app wires it to the player and the library. */
interface ShellHost {
    val library: LibraryIndex
    val player: PlayerUiState
    val favoriteIds: Set<Long>
    val mostPlayed: List<Song>
    /** The synced lyric line being sung right now, if any. */
    val currentLyric: String?

    fun play(songs: List<Song>, index: Int = 0)
    fun shuffle(songs: List<Song>)
    fun playNext(songs: List<Song>)
    fun addToQueue(songs: List<Song>)
    fun togglePlay()
    fun skip()
    fun previous()
    fun seek(positionMs: Long)
    fun toggleShuffle()
    fun cycleRepeat()
    fun toggleFavorite(songId: Long)
    fun setSleep(minutes: Int)
    fun setSpeed(speed: Float)
}

/**
 * A tiny music shell: `play soda stereo`, `queue eres`, `sleep 30m`… Commands resolve free
 * text against the library (artist, album or song, accent-insensitive) and answer in short,
 * lower-case lines, the way a CLI would.
 */
class Shell(private val host: ShellHost, private val text: ShellText) {

    private class Command(
        val name: String,
        val usage: String,
        val aliases: List<String> = emptyList(),
        val takesQuery: Boolean = false,
        val run: (String) -> List<ShellLine>,
    )

    private val commands: List<Command> = listOf(
        Command("play", "play [${text.queryArg}]", listOf("p"), takesQuery = true) { q ->
            if (q.isBlank()) resume() else withMatch(q) { m -> host.play(m.songs); listOf(ok("▶ ${m.label}")) }
        },
        Command("shuffle", "shuffle [${text.somethingArg}]", listOf("mix"), takesQuery = true) { q ->
            if (q.isBlank()) {
                host.shuffle(host.library.songs)
                listOf(ok(text.shuffledLibrary(text.songs(host.library.songs.size))))
            } else {
                withMatch(q) { m -> host.shuffle(m.songs); listOf(ok("⤮ ${m.label}")) }
            }
        },
        Command("queue", "queue <${text.somethingArg}>", listOf("q", "add"), takesQuery = true) { q ->
            requireQuery(q, "queue") { withMatch(q) { m -> host.addToQueue(m.songs); listOf(ok(text.queued(m.label))) } }
        },
        Command("next", "next [${text.somethingArg}]", listOf("n", "skip"), takesQuery = true) { q ->
            if (q.isBlank()) {
                host.skip()
                listOf(ok(text.skipped))
            } else {
                withMatch(q) { m -> host.playNext(m.songs); listOf(ok(text.upNext(m.label))) }
            }
        },
        Command("prev", "prev", listOf("back")) { _ -> host.previous(); listOf(ok(text.previous)) },
        Command("pause", "pause", listOf("stop")) { _ ->
            if (!host.player.isPlaying) listOf(dim(text.alreadyPaused)) else { host.togglePlay(); listOf(ok(text.paused)) }
        },
        Command("now", "now", listOf("np", "status")) { _ -> now() },
        Command("seek", "seek <1:30|+10|-10>") { arg -> seek(arg) },
        Command("fav", "fav", listOf("like")) { _ -> fav() },
        Command("sleep", "sleep <30m|1h|end|off>") { arg -> sleep(arg) },
        Command("speed", "speed <0.5–2>") { arg -> speed(arg) },
        Command("repeat", "repeat") { _ -> repeat() },
        Command("random", "random") { _ ->
            // The state is read before the toggle lands, so it still shows the old mode.
            val wasOn = host.player.shuffleEnabled
            host.toggleShuffle()
            listOf(ok(text.shuffleState(on = !wasOn)))
        },
        Command("top", "top") { _ -> top() },
        Command("ls", "ls", listOf("stats")) { _ -> ls() },
        Command("help", "help", listOf("?", "man")) { _ -> help() },
        // Handled by the terminal UI itself; listed here for help and completion.
        Command(CLEAR, CLEAR, listOf("cls")) { _ -> emptyList() },
        Command(EXIT, EXIT, listOf("quit")) { _ -> emptyList() },
    )

    /** Every command name, for checking that each language describes them all. */
    internal val commandNames: List<String> get() = commands.map { it.name }

    private val byName: Map<String, Command> =
        commands.flatMap { c -> (c.aliases + c.name).map { it to c } }.toMap()

    /** Banner printed when the terminal opens. */
    fun banner(): List<ShellLine> {
        val lib = host.library
        return listOf(
            ShellLine("player · " + text.librarySummary(lib.songs.size, lib.albums.size, lib.artists.size), LineKind.DIM),
            ShellLine(text.bannerHint, LineKind.DIM),
        )
    }

    fun run(rawInput: String): List<ShellLine> {
        val input = rawInput.trim()
        if (input.isEmpty()) return emptyList()
        val echo = ShellLine(input, LineKind.INPUT)

        var (word, rest) = split(input)
        val sudo = word == "sudo"
        if (sudo) {
            if (rest.isBlank()) return listOf(echo, err(text.sudoWhichCommand))
            split(rest).let { word = it.first; rest = it.second }
        }
        if (word == "whoami") return listOf(echo, ShellLine("daniel zúñiga"))
        val command = byName[word]
            ?: return listOf(echo, err(text.notFound(word)))
        val output = command.run(rest)
        return listOf(echo) + (if (sudo) listOf(dim(text.sudoNotNeeded)) else emptyList()) + output
    }

    /**
     * Completions for what's typed so far: command names while on the first word, then library
     * matches (artists, albums, songs) for commands that take free text. Each is a full line.
     */
    fun suggest(rawInput: String, limit: Int = 6): List<String> {
        val input = rawInput.trimStart()
        if (input.isEmpty()) return listOf("play ", "now", "shuffle", "top", "help")
        val (word, rest) = split(input)
        if (!input.contains(' ')) {
            return commands.map { it.name }.filter { it.startsWith(word) && it != word }
                .map { if (byName[it]!!.takesQuery) "$it " else it }
                .take(limit)
        }
        val command = byName[word] ?: return emptyList()
        if (!command.takesQuery || rest.isBlank()) return emptyList()
        return candidates(rest).map { "$word ${it.completion}" }.distinct().take(limit)
    }

    // ---------------------------------------------------------------- matching

    private data class Match(val songs: List<Song>, val label: String, val completion: String, val score: Int)

    private fun withMatch(query: String, block: (Match) -> List<ShellLine>): List<ShellLine> {
        val match = candidates(query).firstOrNull()
            ?: return listOf(err(text.noMatch(query)))
        return block(match)
    }

    /**
     * Ranked matches across artists, albums and songs. A leading `artist`, `album` or `song`
     * restricts the kind. Exact names beat prefixes, prefixes beat substrings; ties go to the
     * bigger collection (artist > album > song) since that's usually what was meant.
     */
    private fun candidates(query: String): List<Match> {
        val (first, afterFirst) = split(query)
        val kind = when (first) {
            "artist", "artista" -> Kind.ARTIST
            "album", "álbum" -> Kind.ALBUM
            "song", "cancion", "canción" -> Kind.SONG
            else -> null
        }
        val needle = (if (kind != null) afterFirst else query).normalizedForSearch()
        if (needle.isEmpty()) return emptyList()
        val lib = host.library
        val found = mutableListOf<Match>()
        if (kind == null || kind == Kind.ARTIST) lib.artists.forEach { a -> score(a.name, needle)?.let { found += artistMatch(a, it) } }
        if (kind == null || kind == Kind.ALBUM) lib.albums.forEach { al -> score(al.title, needle)?.let { found += albumMatch(al, it) } }
        if (kind == null || kind == Kind.SONG) lib.songs.forEach { s -> score(s.title, needle)?.let { found += songMatch(s, it) } }
        return found.sortedByDescending { it.score }
    }

    private enum class Kind { ARTIST, ALBUM, SONG }

    private fun score(name: String, needle: String): Int? {
        val hay = name.normalizedForSearch()
        return when {
            hay == needle -> 300
            hay.startsWith(needle) -> 200
            hay.contains(needle) -> 100
            else -> null
        }
    }

    private fun artistMatch(a: Artist, score: Int) =
        Match(a.songs, "${a.name} · ${text.songs(a.songs.size)}", a.name.lowercase(), score + 3)

    private fun albumMatch(al: Album, score: Int) =
        Match(al.songs, "${al.title} — ${al.artist} · ${text.songs(al.songs.size)}", al.title.lowercase(), score + 2)

    private fun songMatch(s: Song, score: Int) =
        Match(listOf(s), "${s.title} — ${s.artist}", s.title.lowercase(), score + 1)

    // ---------------------------------------------------------------- commands

    private fun resume(): List<ShellLine> {
        if (host.player.nowPlaying == null) return listOf(dim(text.nothingLoaded))
        if (host.player.isPlaying) return listOf(dim(text.alreadyPlaying))
        host.togglePlay()
        return listOf(ok(text.resumed))
    }

    private fun now(): List<ShellLine> {
        val state = host.player
        val np = state.nowPlaying ?: return listOf(dim("nada sonando."))
        val bar = progressBar(state.progress())
        val flags = listOfNotNull(
            if (state.isPlaying) "▶" else "‖",
            text.shuffleFlag.takeIf { state.shuffleEnabled },
            when (state.repeatMode) {
                Player.REPEAT_MODE_ONE -> text.repeatOneFlag
                Player.REPEAT_MODE_ALL -> text.repeatAllFlag
                else -> null
            },
            formatSpeed(state.playbackSpeed).takeIf { state.playbackSpeed != 1f },
            "♥".takeIf { np.songId in host.favoriteIds },
        ).joinToString(" · ")
        return listOfNotNull(
            ShellLine(np.title),
            ShellLine(np.artist, LineKind.DIM),
            ShellLine("$bar ${formatDuration(state.positionMs)} / ${formatDuration(state.durationMs)}"),
            ShellLine(flags, LineKind.DIM),
            host.currentLyric?.takeIf { it.isNotBlank() }?.let { ShellLine("♪ $it", LineKind.OK) },
        )
    }

    private fun seek(arg: String): List<ShellLine> {
        val state = host.player
        if (state.nowPlaying == null || state.durationMs <= 0) return listOf(dim("nada sonando."))
        val target = parseSeek(arg.trim(), state.positionMs)
            ?: return listOf(err(text.seekUsage))
        val clamped = target.coerceIn(0L, state.durationMs)
        host.seek(clamped)
        return listOf(ok("→ ${formatDuration(clamped)}"))
    }

    private fun fav(): List<ShellLine> {
        val id = host.player.nowPlaying?.songId ?: return listOf(dim("nada sonando."))
        val wasFav = id in host.favoriteIds
        host.toggleFavorite(id)
        return listOf(ok(if (wasFav) text.favoriteRemoved else text.favoriteAdded))
    }

    private fun sleep(arg: String): List<ShellLine> {
        val a = arg.trim().lowercase()
        val minutes = when {
            a == "off" || a == "0" -> SessionCommands.SLEEP_OFF
            a == "end" || a == "fin" -> SessionCommands.SLEEP_END_OF_TRACK
            a.endsWith("h") -> a.dropLast(1).toIntOrNull()?.times(60)
            a.endsWith("m") -> a.dropLast(1).toIntOrNull()
            else -> a.toIntOrNull()
        }?.takeIf { it >= SessionCommands.SLEEP_END_OF_TRACK && it <= 24 * 60 }
            ?: return listOf(err(text.sleepUsage))
        host.setSleep(minutes)
        return listOf(
            ok(
                when (minutes) {
                    SessionCommands.SLEEP_OFF -> text.sleepOff
                    SessionCommands.SLEEP_END_OF_TRACK -> text.sleepEndOfTrack
                    else -> text.sleepIn(formatMinutes(minutes))
                },
            ),
        )
    }

    private fun speed(arg: String): List<ShellLine> {
        val value = arg.trim().removeSuffix("x").replace(',', '.').toFloatOrNull()
            ?.takeIf { it in 0.5f..2f }
            ?: return listOf(err(text.speedUsage))
        host.setSpeed(value)
        return listOf(ok(text.speedSet(formatSpeed(value))))
    }

    private fun repeat(): List<ShellLine> {
        val next = when (host.player.repeatMode) {
            Player.REPEAT_MODE_OFF -> "all"
            Player.REPEAT_MODE_ALL -> "one"
            else -> "off"
        }
        host.cycleRepeat()
        return listOf(ok(text.repeatState(next)))
    }

    private fun top(): List<ShellLine> {
        val songs = host.mostPlayed.take(5)
        if (songs.isEmpty()) return listOf(dim(text.noHistory))
        return songs.mapIndexed { i, s -> ShellLine("%02d  %s — %s".format(i + 1, s.title, s.artist)) }
    }

    private fun ls(): List<ShellLine> {
        val lib = host.library
        val total = lib.songs.sumOf { it.durationMs }
        return listOf(
            ShellLine(text.librarySummary(lib.songs.size, lib.albums.size, lib.artists.size)),
            ShellLine(text.musicAndFavorites(formatHours(total), host.favoriteIds.size), LineKind.DIM),
        )
    }

    private fun help(): List<ShellLine> {
        // Two lines per command: a phone is too narrow for an aligned second column.
        return commands.flatMap { c -> listOf(ShellLine(c.usage), ShellLine("  " + text.help(c.name), LineKind.DIM)) } +
            ShellLine(text.helpTip, LineKind.DIM)
    }

    private inline fun requireQuery(q: String, name: String, block: () -> List<ShellLine>): List<ShellLine> =
        if (q.isBlank()) listOf(err(text.usage(name, "<${text.queryArg}>"))) else block()

    // ---------------------------------------------------------------- helpers

    private fun ok(text: String) = ShellLine(text, LineKind.OK)
    private fun err(text: String) = ShellLine(text, LineKind.ERR)
    private fun dim(text: String) = ShellLine(text, LineKind.DIM)

    /** `clear`/`exit` (or an alias) typed as the whole line; the UI acts on these itself. */
    fun uiAction(rawInput: String): String? {
        val (word, rest) = split(rawInput)
        val name = byName[word]?.name
        return name.takeIf { rest.isBlank() && (it == CLEAR || it == EXIT) }
    }

    companion object {
        const val CLEAR = "clear"
        const val EXIT = "exit"
        private const val BAR_CELLS = 20

        private fun split(input: String): Pair<String, String> {
            val trimmed = input.trim()
            val space = trimmed.indexOf(' ')
            return if (space < 0) trimmed.lowercase() to "" else trimmed.substring(0, space).lowercase() to trimmed.substring(space + 1).trim()
        }

        internal fun progressBar(fraction: Float): String {
            val filled = (fraction.coerceIn(0f, 1f) * BAR_CELLS).toInt()
            return "[" + "█".repeat(filled) + "░".repeat(BAR_CELLS - filled) + "]"
        }

        internal fun parseSeek(arg: String, currentMs: Long): Long? {
            if (arg.startsWith("+") || arg.startsWith("-")) {
                val seconds = arg.drop(1).toLongOrNull() ?: return null
                return currentMs + (if (arg[0] == '+') seconds else -seconds) * 1000
            }
            val parts = arg.split(':')
            if (parts.size == 2) {
                val min = parts[0].toLongOrNull() ?: return null
                val sec = parts[1].toLongOrNull()?.takeIf { it in 0..59 } ?: return null
                return (min * 60 + sec) * 1000
            }
            return arg.toLongOrNull()?.times(1000)
        }

        private fun formatMinutes(minutes: Int) =
            if (minutes >= 60 && minutes % 60 == 0) "${minutes / 60} h" else "$minutes min"

        private fun formatHours(ms: Long): String {
            val totalMin = ms / 60_000
            return if (totalMin >= 60) "${totalMin / 60} h ${totalMin % 60} min" else "$totalMin min"
        }
    }
}
