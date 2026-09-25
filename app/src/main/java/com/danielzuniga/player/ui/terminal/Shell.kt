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
class Shell(private val host: ShellHost) {

    private class Command(
        val name: String,
        val usage: String,
        val help: String,
        val aliases: List<String> = emptyList(),
        val takesQuery: Boolean = false,
        val run: (String) -> List<ShellLine>,
    )

    private val commands: List<Command> = listOf(
        Command("play", "play [artista|álbum|canción]", "reproduce lo que encuentre; sin nada, reanuda", listOf("p"), takesQuery = true) { q ->
            if (q.isBlank()) resume() else withMatch(q) { m -> host.play(m.songs); listOf(ok("▶ ${m.label}")) }
        },
        Command("shuffle", "shuffle [algo]", "mezcla todo, o solo lo que encuentre", listOf("mix"), takesQuery = true) { q ->
            if (q.isBlank()) {
                host.shuffle(host.library.songs)
                listOf(ok("⤮ toda la biblioteca · ${count(host.library.songs.size)}"))
            } else {
                withMatch(q) { m -> host.shuffle(m.songs); listOf(ok("⤮ ${m.label}")) }
            }
        },
        Command("queue", "queue <algo>", "lo agrega al final de la cola", listOf("q", "add"), takesQuery = true) { q ->
            requireQuery(q, "queue") { withMatch(q) { m -> host.addToQueue(m.songs); listOf(ok("+ en cola: ${m.label}")) } }
        },
        Command("next", "next [algo]", "sin nada salta; con algo, lo pone a continuación", listOf("n", "skip"), takesQuery = true) { q ->
            if (q.isBlank()) {
                host.skip()
                listOf(ok("⏭ siguiente"))
            } else {
                withMatch(q) { m -> host.playNext(m.songs); listOf(ok("↳ a continuación: ${m.label}")) }
            }
        },
        Command("prev", "prev", "vuelve a la anterior", listOf("back")) { _ -> host.previous(); listOf(ok("⏮ anterior")) },
        Command("pause", "pause", "pausa o reanuda", listOf("stop")) { _ ->
            if (!host.player.isPlaying) listOf(dim("ya está en pausa.")) else { host.togglePlay(); listOf(ok("‖ pausa")) }
        },
        Command("now", "now", "qué suena, con progreso y letra", listOf("np", "status")) { _ -> now() },
        Command("seek", "seek <1:30|+10|-10>", "salta a un momento de la canción") { arg -> seek(arg) },
        Command("fav", "fav", "marca o desmarca la actual como favorita", listOf("like")) { _ -> fav() },
        Command("sleep", "sleep <30m|1h|end|off>", "temporizador para dormir") { arg -> sleep(arg) },
        Command("speed", "speed <0.5–2>", "velocidad de reproducción") { arg -> speed(arg) },
        Command("repeat", "repeat", "cicla: off → todo → una") { _ -> repeat() },
        Command("random", "random", "activa o apaga el modo aleatorio de la cola") { _ ->
            host.toggleShuffle()
            listOf(ok(if (host.player.shuffleEnabled) "aleatorio: off" else "aleatorio: on"))
        },
        Command("top", "top", "tus más escuchadas") { _ -> top() },
        Command("ls", "ls", "resumen de la biblioteca", listOf("stats")) { _ -> ls() },
        Command("help", "help", "esta lista", listOf("?", "man")) { _ -> help() },
        // Handled by the terminal UI itself; listed here for help and completion.
        Command(CLEAR, CLEAR, "limpia la pantalla", listOf("cls")) { _ -> emptyList() },
        Command(EXIT, EXIT, "cierra la terminal", listOf("quit")) { _ -> emptyList() },
    )

    private val byName: Map<String, Command> =
        commands.flatMap { c -> (c.aliases + c.name).map { it to c } }.toMap()

    /** Banner printed when the terminal opens. */
    fun banner(): List<ShellLine> {
        val lib = host.library
        return listOf(
            ShellLine("player · ${count(lib.songs.size)} · ${lib.albums.size} álbumes · ${lib.artists.size} artistas", LineKind.DIM),
            ShellLine("escribe `help` para ver los comandos.", LineKind.DIM),
        )
    }

    fun run(rawInput: String): List<ShellLine> {
        val input = rawInput.trim()
        if (input.isEmpty()) return emptyList()
        val echo = ShellLine(input, LineKind.INPUT)

        var (word, rest) = split(input)
        val sudo = word == "sudo"
        if (sudo) {
            if (rest.isBlank()) return listOf(echo, err("sudo: ¿qué comando?"))
            split(rest).let { word = it.first; rest = it.second }
        }
        if (word == "whoami") return listOf(echo, ShellLine("daniel zúñiga"))
        val command = byName[word]
            ?: return listOf(echo, err("$word: comando no encontrado. prueba `help`."))
        val output = command.run(rest)
        return listOf(echo) + (if (sudo) listOf(dim("no hace falta root aquí, brother.")) else emptyList()) + output
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
            ?: return listOf(err("nada coincide con \"$query\"."))
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
        Match(a.songs, "${a.name} · ${count(a.songs.size)}", a.name.lowercase(), score + 3)

    private fun albumMatch(al: Album, score: Int) =
        Match(al.songs, "${al.title} — ${al.artist} · ${count(al.songs.size)}", al.title.lowercase(), score + 2)

    private fun songMatch(s: Song, score: Int) =
        Match(listOf(s), "${s.title} — ${s.artist}", s.title.lowercase(), score + 1)

    // ---------------------------------------------------------------- commands

    private fun resume(): List<ShellLine> {
        if (host.player.nowPlaying == null) return listOf(dim("nada cargado. prueba `play <algo>` o `shuffle`."))
        if (host.player.isPlaying) return listOf(dim("ya está sonando."))
        host.togglePlay()
        return listOf(ok("▶ reanudado"))
    }

    private fun now(): List<ShellLine> {
        val state = host.player
        val np = state.nowPlaying ?: return listOf(dim("nada sonando."))
        val bar = progressBar(state.progress())
        val flags = listOfNotNull(
            if (state.isPlaying) "▶" else "‖",
            "aleatorio".takeIf { state.shuffleEnabled },
            when (state.repeatMode) {
                Player.REPEAT_MODE_ONE -> "repite una"
                Player.REPEAT_MODE_ALL -> "repite todo"
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
            ?: return listOf(err("uso: seek 1:30 · seek +10 · seek -10"))
        val clamped = target.coerceIn(0L, state.durationMs)
        host.seek(clamped)
        return listOf(ok("→ ${formatDuration(clamped)}"))
    }

    private fun fav(): List<ShellLine> {
        val id = host.player.nowPlaying?.songId ?: return listOf(dim("nada sonando."))
        val wasFav = id in host.favoriteIds
        host.toggleFavorite(id)
        return listOf(ok(if (wasFav) "♡ quitada de favoritas" else "♥ añadida a favoritas"))
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
            ?: return listOf(err("uso: sleep 30m · sleep 1h · sleep end · sleep off"))
        host.setSleep(minutes)
        return listOf(
            ok(
                when (minutes) {
                    SessionCommands.SLEEP_OFF -> "temporizador apagado"
                    SessionCommands.SLEEP_END_OF_TRACK -> "☾ pausa al terminar esta canción"
                    else -> "☾ pausa en ${formatMinutes(minutes)}"
                },
            ),
        )
    }

    private fun speed(arg: String): List<ShellLine> {
        val value = arg.trim().removeSuffix("x").replace(',', '.').toFloatOrNull()
            ?.takeIf { it in 0.5f..2f }
            ?: return listOf(err("uso: speed 1.25 (entre 0.5 y 2)"))
        host.setSpeed(value)
        return listOf(ok("velocidad ${formatSpeed(value)}"))
    }

    private fun repeat(): List<ShellLine> {
        val next = when (host.player.repeatMode) {
            Player.REPEAT_MODE_OFF -> "todo"
            Player.REPEAT_MODE_ALL -> "una"
            else -> "off"
        }
        host.cycleRepeat()
        return listOf(ok("repetir: $next"))
    }

    private fun top(): List<ShellLine> {
        val songs = host.mostPlayed.take(5)
        if (songs.isEmpty()) return listOf(dim("todavía no hay historial. dale play a algo."))
        return songs.mapIndexed { i, s -> ShellLine("%02d  %s — %s".format(i + 1, s.title, s.artist)) }
    }

    private fun ls(): List<ShellLine> {
        val lib = host.library
        val total = lib.songs.sumOf { it.durationMs }
        return listOf(
            ShellLine("${count(lib.songs.size)} · ${lib.albums.size} álbumes · ${lib.artists.size} artistas"),
            ShellLine("${formatHours(total)} de música · ${host.favoriteIds.size} favoritas", LineKind.DIM),
        )
    }

    private fun help(): List<ShellLine> {
        // Two lines per command: a phone is too narrow for an aligned second column.
        return commands.flatMap { c -> listOf(ShellLine(c.usage), ShellLine("  " + c.help, LineKind.DIM)) } +
            ShellLine("tip: `play album signos` o `play artist soda` para ser específico.", LineKind.DIM)
    }

    private inline fun requireQuery(q: String, name: String, block: () -> List<ShellLine>): List<ShellLine> =
        if (q.isBlank()) listOf(err("uso: $name <artista|álbum|canción>")) else block()

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

        private fun count(n: Int) = if (n == 1) "1 canción" else "$n canciones"

        private fun formatMinutes(minutes: Int) =
            if (minutes >= 60 && minutes % 60 == 0) "${minutes / 60} h" else "$minutes min"

        private fun formatHours(ms: Long): String {
            val totalMin = ms / 60_000
            return if (totalMin >= 60) "${totalMin / 60} h ${totalMin % 60} min" else "$totalMin min"
        }
    }
}
