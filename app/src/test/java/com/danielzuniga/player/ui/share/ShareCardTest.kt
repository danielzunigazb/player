package com.danielzuniga.player.ui.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.danielzuniga.player.data.Song
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShareCardTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val song = Song(1, "De Música Ligera", "Soda Stereo", "Canción Animal", 10, 210_000)

    /** A stand-in cover: a warm gradient, like a real album. */
    private fun cover(): Bitmap = Bitmap.createBitmap(600, 600, Bitmap.Config.ARGB_8888).also {
        Canvas(it).drawPaint(Paint().apply {
            shader = LinearGradient(0f, 0f, 600f, 600f, Color.rgb(140, 40, 50), Color.rgb(30, 20, 30), Shader.TileMode.CLAMP)
        })
    }

    /** Saves a render next to the build outputs, to look at when changing the design. */
    private fun save(bitmap: Bitmap, name: String) {
        val dir = File("build/share-cards").apply { mkdirs() }
        File(dir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun songCardIsAStoryWithTheCoverAndTheObsidianPage() {
        val card = ShareCard.render(context, ShareCardContent(song.title, song.artist, song.album, cover()))
        save(card, "song.png")
        assertEquals(ShareCard.WIDTH, card.width)
        assertEquals(ShareCard.HEIGHT, card.height)
        assertEquals(0xFF0E0C0A.toInt(), card.getPixel(10, 10))
        // The cover block fills the middle of the card.
        assertEquals(Color.red(cover().getPixel(300, 300)).toFloat(), Color.red(card.getPixel(540, 720)).toFloat(), 40f)
    }

    @Test
    fun lyricsCardAndCardsWithoutCover() {
        val lines = listOf("Ella durmió al calor de las masas", "y yo desperté queriendo soñarla")
        save(ShareCard.render(context, ShareCardContent(song.title, song.artist, song.album, cover(), lines)), "lyrics.png")
        val bare = ShareCard.render(context, ShareCardContent("Un título muy largo que no cabe en una sola línea de la tarjeta", "Artista", "", null))
        save(bare, "no-cover.png")
        assertEquals(ShareCard.HEIGHT, bare.height)
    }

    @Test
    fun sharesAnImageWithACaptionThroughTheChooser() {
        assertEquals("De Música Ligera — Soda Stereo", SongSharer.caption(song, emptyList()))
        assertEquals(
            "“Ella durmió\nal calor”\nDe Música Ligera — Soda Stereo",
            SongSharer.caption(song, listOf(" Ella durmió ", "", "al calor")),
        )

        val uri = Uri.parse("content://com.danielzuniga.player.share/share/card.png")
        val chooser = SongSharer.chooser(context, uri, "caption")
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        @Suppress("DEPRECATION")
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals("image/png", send.type)
        @Suppress("DEPRECATION")
        assertEquals(uri, send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test
    fun cardFilesAreServedByTheFileProvider() {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "card.png").apply { writeBytes(byteArrayOf(1)) }
        assertEquals("content", SongSharer.uriFor(context, file).scheme)
    }

    @Test
    fun aNewCardKeepsThePreviousOneForTheAppStillReadingIt() {
        File(context.cacheDir, "share").deleteRecursively()
        val first = onMainLooper { SongSharer.createCard(context, song, emptyList()) }
        Thread.sleep(2)
        val second = onMainLooper { SongSharer.createCard(context, song, emptyList()) }
        Thread.sleep(2)
        val third = onMainLooper { SongSharer.createCard(context, song, emptyList()) }
        assertTrue(second.exists() && third.exists())
        assertFalse(first.exists())
    }

    @Test
    fun aCardThatCantBeWrittenIsReportedInsteadOfCrashing() {
        // A file where the share folder should be: the card has nowhere to go.
        val dir = File(context.cacheDir, "share").apply { deleteRecursively() }
        dir.writeBytes(byteArrayOf(1))
        try {
            assertFalse(onMainLooper { SongSharer.share(context, song) })
        } finally {
            dir.delete()
        }
    }

    /** Runs [block] off the test thread while the main looper keeps turning (Coil needs it). */
    private fun <T> onMainLooper(block: suspend () -> T): T {
        val result = CoroutineScope(Dispatchers.IO).async { block() }
        while (!result.isCompleted) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        return runBlocking { result.await() }
    }
}
