package com.danielzuniga.player.ui.terminal

/**
 * Everything the shell says, per language. Command names (`play`, `queue`…) stay the same in
 * every language, like a real CLI; descriptions and replies are translated. Being an interface,
 * a new language won't compile until it has every reply; ShellTest checks every command has a
 * description in each one.
 */
interface ShellText {
    /** Placeholder for "an artist, album or song" in usage lines. */
    val queryArg: String
    /** Placeholder for "anything" in usage lines. */
    val somethingArg: String

    /** One-line description of each command, by command name. */
    fun help(command: String): String
    val helpTip: String

    fun songs(n: Int): String
    fun librarySummary(songs: Int, albums: Int, artists: Int): String
    val bannerHint: String

    fun notFound(word: String): String
    val sudoWhichCommand: String
    val sudoNotNeeded: String
    fun noMatch(query: String): String
    fun usage(command: String, args: String): String

    fun shuffledLibrary(count: String): String
    fun queued(label: String): String
    val skipped: String
    fun upNext(label: String): String
    val previous: String
    val alreadyPaused: String
    val paused: String
    val nothingLoaded: String
    val alreadyPlaying: String
    val resumed: String
    val nothingPlaying: String

    val shuffleFlag: String
    val repeatOneFlag: String
    val repeatAllFlag: String
    fun shuffleState(on: Boolean): String
    /** [mode] is one of "all", "one" or "off". */
    fun repeatState(mode: String): String

    val favoriteAdded: String
    val favoriteRemoved: String

    val sleepUsage: String
    val sleepOff: String
    val sleepEndOfTrack: String
    fun sleepIn(duration: String): String

    val speedUsage: String
    fun speedSet(speed: String): String

    val seekUsage: String
    val noHistory: String
    val sharing: String
    fun sharingLyric(line: String): String
    val noLyricLine: String
    fun musicAndFavorites(duration: String, favorites: Int): String

    companion object {
        fun forLanguage(language: String): ShellText = if (language == "es") Spanish else English
    }

    object Spanish : ShellText {
        override val queryArg = "artista|álbum|canción"
        override val somethingArg = "algo"
        override fun help(command: String) = when (command) {
            "play" -> "reproduce lo que encuentre; sin nada, reanuda"
            "shuffle" -> "mezcla todo, o solo lo que encuentre"
            "queue" -> "lo agrega al final de la cola"
            "next" -> "sin nada salta; con algo, lo pone a continuación"
            "prev" -> "vuelve a la anterior"
            "pause" -> "pausa o reanuda"
            "now" -> "qué suena, con progreso y letra"
            "seek" -> "salta a un momento de la canción"
            "fav" -> "marca o desmarca la actual como favorita"
            "sleep" -> "temporizador para dormir"
            "speed" -> "velocidad de reproducción"
            "repeat" -> "cicla: off → todo → una"
            "random" -> "activa o apaga el modo aleatorio de la cola"
            "share" -> "comparte la canción como imagen; `share lyric`, con la línea que suena"
            "top" -> "tus más escuchadas"
            "ls" -> "resumen de la biblioteca"
            "help" -> "esta lista"
            "clear" -> "limpia la pantalla"
            "exit" -> "cierra la terminal"
            else -> ""
        }
        override val helpTip = "tip: `play album signos` o `play artist soda` para ser específico."
        override fun songs(n: Int) = if (n == 1) "1 canción" else "$n canciones"
        override fun librarySummary(songs: Int, albums: Int, artists: Int) =
            "${songs(songs)} · $albums álbumes · $artists artistas"
        override val bannerHint = "escribe `help` para ver los comandos."
        override fun notFound(word: String) = "$word: comando no encontrado. prueba `help`."
        override val sudoWhichCommand = "sudo: ¿qué comando?"
        override val sudoNotNeeded = "no hace falta root aquí, brother."
        override fun noMatch(query: String) = "nada coincide con \"$query\"."
        override fun usage(command: String, args: String) = "uso: $command $args"
        override fun shuffledLibrary(count: String) = "⤮ toda la biblioteca · $count"
        override fun queued(label: String) = "+ en cola: $label"
        override val skipped = "⏭ siguiente"
        override fun upNext(label: String) = "↳ a continuación: $label"
        override val previous = "⏮ anterior"
        override val alreadyPaused = "ya está en pausa."
        override val paused = "‖ pausa"
        override val nothingLoaded = "nada cargado. prueba `play <algo>` o `shuffle`."
        override val alreadyPlaying = "ya está sonando."
        override val resumed = "▶ reanudado"
        override val nothingPlaying = "nada sonando."
        override val shuffleFlag = "aleatorio"
        override val repeatOneFlag = "repite una"
        override val repeatAllFlag = "repite todo"
        override fun shuffleState(on: Boolean) = if (on) "aleatorio: on" else "aleatorio: off"
        override fun repeatState(mode: String) = "repetir: " + when (mode) {
            "all" -> "todo"
            "one" -> "una"
            else -> "off"
        }
        override val favoriteAdded = "♥ añadida a favoritas"
        override val favoriteRemoved = "♡ quitada de favoritas"
        override val sleepUsage = "uso: sleep 30m · sleep 1h · sleep end · sleep off"
        override val sleepOff = "temporizador apagado"
        override val sleepEndOfTrack = "☾ pausa al terminar esta canción"
        override fun sleepIn(duration: String) = "☾ pausa en $duration"
        override val speedUsage = "uso: speed 1.25 (entre 0.5 y 2)"
        override fun speedSet(speed: String) = "velocidad $speed"
        override val seekUsage = "uso: seek 1:30 · seek +10 · seek -10"
        override val noHistory = "todavía no hay historial. dale play a algo."
        override val sharing = "↗ compartiendo la canción…"
        override fun sharingLyric(line: String) = "↗ compartiendo \"$line\"…"
        override val noLyricLine = "no hay una línea de letra sonando ahora."
        override fun musicAndFavorites(duration: String, favorites: Int) = "$duration de música · $favorites favoritas"
    }

    object English : ShellText {
        override val queryArg = "artist|album|song"
        override val somethingArg = "something"
        override fun help(command: String) = when (command) {
            "play" -> "plays whatever matches; on its own, resumes"
            "shuffle" -> "shuffles everything, or just what matches"
            "queue" -> "adds it to the end of the queue"
            "next" -> "on its own skips; with a query, plays it next"
            "prev" -> "goes back to the previous song"
            "pause" -> "pauses or resumes"
            "now" -> "what's playing, with progress and lyrics"
            "seek" -> "jumps to a point in the song"
            "fav" -> "marks or unmarks the current song as a favorite"
            "sleep" -> "sleep timer"
            "speed" -> "playback speed"
            "repeat" -> "cycles: off → all → one"
            "random" -> "turns queue shuffle on or off"
            "share" -> "shares the song as an image; `share lyric`, with the line being sung"
            "top" -> "your most played"
            "ls" -> "library summary"
            "help" -> "this list"
            "clear" -> "clears the screen"
            "exit" -> "closes the terminal"
            else -> ""
        }
        override val helpTip = "tip: `play album signos` or `play artist soda` to be specific."
        override fun songs(n: Int) = if (n == 1) "1 song" else "$n songs"
        override fun librarySummary(songs: Int, albums: Int, artists: Int) =
            "${songs(songs)} · $albums albums · $artists artists"
        override val bannerHint = "type `help` to see the commands."
        override fun notFound(word: String) = "$word: command not found. try `help`."
        override val sudoWhichCommand = "sudo: which command?"
        override val sudoNotNeeded = "no root needed here, friend."
        override fun noMatch(query: String) = "nothing matches \"$query\"."
        override fun usage(command: String, args: String) = "usage: $command $args"
        override fun shuffledLibrary(count: String) = "⤮ whole library · $count"
        override fun queued(label: String) = "+ queued: $label"
        override val skipped = "⏭ next"
        override fun upNext(label: String) = "↳ up next: $label"
        override val previous = "⏮ previous"
        override val alreadyPaused = "already paused."
        override val paused = "‖ paused"
        override val nothingLoaded = "nothing loaded. try `play <something>` or `shuffle`."
        override val alreadyPlaying = "already playing."
        override val resumed = "▶ resumed"
        override val nothingPlaying = "nothing playing."
        override val shuffleFlag = "shuffle"
        override val repeatOneFlag = "repeat one"
        override val repeatAllFlag = "repeat all"
        override fun shuffleState(on: Boolean) = if (on) "shuffle: on" else "shuffle: off"
        override fun repeatState(mode: String) = "repeat: $mode"
        override val favoriteAdded = "♥ added to favorites"
        override val favoriteRemoved = "♡ removed from favorites"
        override val sleepUsage = "usage: sleep 30m · sleep 1h · sleep end · sleep off"
        override val sleepOff = "sleep timer off"
        override val sleepEndOfTrack = "☾ pausing when this song ends"
        override fun sleepIn(duration: String) = "☾ pausing in $duration"
        override val speedUsage = "usage: speed 1.25 (between 0.5 and 2)"
        override fun speedSet(speed: String) = "speed $speed"
        override val seekUsage = "usage: seek 1:30 · seek +10 · seek -10"
        override val noHistory = "no history yet. play something."
        override val sharing = "↗ sharing the song…"
        override fun sharingLyric(line: String) = "↗ sharing \"$line\"…"
        override val noLyricLine = "no lyric line is playing right now."
        override fun musicAndFavorites(duration: String, favorites: Int) = "$duration of music · $favorites favorites"
    }
}
