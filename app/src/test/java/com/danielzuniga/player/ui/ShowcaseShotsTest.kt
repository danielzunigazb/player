package com.danielzuniga.player.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil.Coil
import coil.ImageLoader
import coil.decode.DataSource
import coil.intercept.Interceptor
import coil.request.SuccessResult
import com.danielzuniga.player.data.LibraryIndex
import com.danielzuniga.player.data.Song
import com.danielzuniga.player.data.ThemeMode
import com.danielzuniga.player.data.lyrics.LyricLine
import com.danielzuniga.player.data.lyrics.Lyrics
import com.danielzuniga.player.data.lyrics.LyricsSource
import com.danielzuniga.player.data.update.UpdateState
import com.danielzuniga.player.playback.NowPlaying
import com.danielzuniga.player.playback.PlayerUiState
import com.danielzuniga.player.ui.components.LocalCurrentSongId
import com.danielzuniga.player.ui.components.LocalIsPlaying
import com.danielzuniga.player.ui.components.LocalSongActions
import com.danielzuniga.player.ui.components.SongActions
import com.danielzuniga.player.ui.library.HomeScreen
import com.danielzuniga.player.ui.player.MiniPlayer
import com.danielzuniga.player.ui.player.NowPlayingActions
import com.danielzuniga.player.ui.player.NowPlayingScreen
import com.danielzuniga.player.ui.settings.AppLanguage
import com.danielzuniga.player.ui.settings.SettingsScreen
import com.danielzuniga.player.ui.share.ShareCard
import com.danielzuniga.player.ui.share.ShareCardContent
import com.danielzuniga.player.ui.terminal.Shell
import com.danielzuniga.player.ui.terminal.ShellHost
import com.danielzuniga.player.ui.terminal.ShellLine
import com.danielzuniga.player.ui.terminal.ShellText
import com.danielzuniga.player.ui.terminal.TerminalScreen
import com.danielzuniga.player.ui.theme.PlayerTheme
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The app screens the showcase video uses, drawn from the real UI so the video always shows
 * the current app. Skipped in normal test runs; the Showcase workflow (or anyone) runs it with
 * `./gradlew testDebugUnitTest --tests '*ShowcaseShotsTest*' -PshowcaseShots=<dir>`.
 * 400×860 dp at xxhdpi: 1200×2580 PNGs.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "es-w400dp-h860dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShowcaseShotsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val outDir: File? = System.getProperty("shots.dir")?.let(::File)
    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** Covers are gradients in the design system's colors, a different one per album. */
    private val palette = listOf(
        0xFF8C2F39.toInt() to 0xFF1B1713.toInt(),
        0xFF2B6A5C.toInt() to 0xFF0E0C0A.toInt(),
        0xFFD6A23E.toInt() to 0xFF5C3A10.toInt(),
        0xFF3A4A6B.toInt() to 0xFF10131B.toInt(),
        0xFFB0381F.toInt() to 0xFF2F2923.toInt(),
    )

    private fun cover(key: String, size: Int = 256): Bitmap {
        val (c1, c2) = palette[(key.hashCode() and 0x7fffffff) % palette.size]
        return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bmp ->
            val canvas = Canvas(bmp)
            val s = size.toFloat()
            canvas.drawRect(0f, 0f, s, s, Paint().apply { shader = LinearGradient(0f, 0f, s, s, c1, c2, Shader.TileMode.CLAMP) })
            canvas.drawCircle(s * 0.66f, s * 0.39f, s * 0.23f, Paint().apply { color = 0x33FFFFFF; isAntiAlias = true })
        }
    }

    @Before
    fun setUp() {
        assumeTrue("screenshots only when -PshowcaseShots=<dir> is given", outDir != null)
        outDir!!.mkdirs()
        Coil.setImageLoader(
            ImageLoader.Builder(context).components {
                add(Interceptor { chain ->
                    SuccessResult(BitmapDrawable(context.resources, cover(chain.request.data.toString())), chain.request, DataSource.MEMORY)
                })
            }.build(),
        )
    }

    private val songs = listOf(
        Song(1, "De Música Ligera", "Soda Stereo", "Canción Animal", 10, 210_000, track = 1),
        Song(2, "Un Millón de Años Luz", "Soda Stereo", "Canción Animal", 10, 280_000, track = 2),
        Song(3, "Eres", "Café Tacvba", "Cuatro Caminos", 12, 250_000, track = 1),
        Song(4, "La Flaca", "Jarabe de Palo", "La Flaca", 13, 260_000, track = 1),
        Song(5, "Clandestino", "Manu Chao", "Clandestino", 14, 150_000, track = 1),
        Song(6, "Persiana Americana", "Soda Stereo", "Signos", 11, 280_000, track = 1),
        Song(7, "24K", "T3R Elemento", "La Divinidad Femenina", 15, 161_000, track = 1),
    )
    private val index = LibraryIndex(songs)
    private val nowPlaying = NowPlaying(1, "De Música Ligera", "Soda Stereo", Uri.parse("content://x/a1"))
    private val playing = PlayerUiState(nowPlaying = nowPlaying, isPlaying = true, positionMs = 84_000, durationMs = 210_000)

    private val lyrics = Lyrics.Synced(
        listOf(
            LyricLine(60_000, "Ella durmió al calor de las masas"),
            LyricLine(66_000, "y yo desperté queriendo soñarla"),
            LyricLine(72_000, "algún tiempo atrás pensé en escribirle"),
            LyricLine(78_000, "que nunca sorteé las trampas del amor"),
            LyricLine(84_000, "De aquel amor"),
            LyricLine(88_000, "de música ligera"),
            LyricLine(92_000, "nada nos libra"),
            LyricLine(96_000, "nada más queda"),
        ),
    )

    private fun shot(name: String) {
        compose.waitForIdle()
        Thread.sleep(800)
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        val root = compose.activity.window.decorView
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { root.draw(Canvas(bmp)) }
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun screen(mode: ThemeMode = ThemeMode.DARK, content: @Composable () -> Unit) {
        compose.setContent {
            PlayerTheme(themeMode = mode) {
                CompositionLocalProvider(
                    LocalSongActions provides SongActions({ _, _ -> }, {}, {}, {}, {}, {}, {}, {}),
                    LocalCurrentSongId provides 6L,
                    LocalIsPlaying provides true,
                ) {
                    Surface { content() }
                }
            }
        }
    }

    private val nowPlayingActions = NowPlayingActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})

    @Test
    fun nowPlaying() {
        screen { NowPlayingScreen(playing, nowPlaying, isFavorite = true, lyrics = LyricsUiState(), actions = nowPlayingActions) }
        shot("dz_now_playing")
    }

    @Test
    fun syncedLyrics() {
        screen {
            NowPlayingScreen(
                playing.copy(positionMs = 85_000),
                nowPlaying,
                isFavorite = true,
                lyrics = LyricsUiState(lyrics = lyrics, source = LyricsSource.ONLINE),
                actions = nowPlayingActions,
            )
        }
        compose.onNodeWithContentDescription("Letra").performClick()
        shot("lyrics_synced")
    }

    private fun home(mode: ThemeMode, name: String) {
        screen(mode) {
            Box(Modifier.fillMaxSize()) {
                HomeScreen(
                    library = LibraryUiState(songs = index.songs, albums = index.albums, artists = index.artists, totalSongs = songs.size, hasScanned = true),
                    playlists = PlaylistsUiState(),
                    onQueryChange = {}, onSortChange = {}, onRefresh = {}, onOpenAlbum = {}, onOpenArtist = {}, onOpenFolder = {},
                    onOpenPlaylist = {}, onOpenSmartPlaylist = {}, onCreatePlaylist = {}, onOpenSettings = {}, contentPadding = PaddingValues(),
                )
                MiniPlayer(
                    state = PlayerUiState(isPlaying = true, positionMs = 90_000, durationMs = 280_000),
                    nowPlaying = NowPlaying(6, "Persiana Americana", "Soda Stereo", Uri.parse("content://x/b2")),
                    onClick = {}, onTogglePlay = {}, onNext = {}, onPrevious = {},
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
        shot(name)
    }

    @Test fun homeDark() = home(ThemeMode.DARK, "dz_home")

    @Test fun homeLight() = home(ThemeMode.LIGHT, "dz_home_light")

    @Test
    fun terminal() {
        val host = object : ShellHost {
            override val library = index
            override val player = playing
            override val favoriteIds = setOf(1L)
            override val mostPlayed = songs.take(3)
            override val currentLyric = "de música ligera"
            override fun play(songs: List<Song>, index: Int) {}
            override fun shuffle(songs: List<Song>) {}
            override fun playNext(songs: List<Song>) {}
            override fun addToQueue(songs: List<Song>) {}
            override fun togglePlay() {}
            override fun skip() {}
            override fun previous() {}
            override fun seek(positionMs: Long) {}
            override fun toggleShuffle() {}
            override fun cycleRepeat() {}
            override fun toggleFavorite(songId: Long) {}
            override fun setSleep(minutes: Int) {}
            override fun setSpeed(speed: Float) {}
            override fun share(lines: List<String>) {}
        }
        val shell = Shell(host, ShellText.Spanish)
        val lines = mutableStateListOf<ShellLine>().apply {
            addAll(shell.banner())
            listOf("play soda stereo", "queue eres", "now", "share lyric").forEach { addAll(shell.run(it)) }
        }
        screen { TerminalScreen(shell = shell, lines = lines, onClose = {}) }
        shot("terminal")
    }

    @Test
    fun settings() {
        screen {
            SettingsScreen(
                state = SettingsUiState(),
                onBack = {}, onThemeMode = {}, language = AppLanguage.SYSTEM, onLanguage = {},
                onDynamicColor = {}, onOnlineLyrics = {}, onMinDuration = {}, onOnlineTags = {},
                autoUpdates = true, onAutoUpdates = {}, updateState = UpdateState.UpToDate, onCheckUpdates = {},
                onRescan = {}, bottomPadding = PaddingValues(),
            )
        }
        shot("settings")
    }

    @Test
    fun shareCards() {
        val art = cover("content://x/a1", 1000)
        val song = ShareCard.render(context, ShareCardContent("De Música Ligera", "Soda Stereo", "Canción Animal", art))
        val lyric = ShareCard.render(
            context,
            ShareCardContent(
                "De Música Ligera", "Soda Stereo", "Canción Animal", art,
                lines = listOf("Ella durmió al calor de las masas", "y yo desperté queriendo soñarla"),
            ),
        )
        File(outDir, "share_song.png").outputStream().use { song.compress(Bitmap.CompressFormat.PNG, 100, it) }
        File(outDir, "share_lyrics.png").outputStream().use { lyric.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
