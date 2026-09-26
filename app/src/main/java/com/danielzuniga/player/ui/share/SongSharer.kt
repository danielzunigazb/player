package com.danielzuniga.player.ui.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.danielzuniga.player.R
import com.danielzuniga.player.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Shares a song as a story image through Android's share sheet, so it reaches Instagram and
 * Snapchat stories, WhatsApp, Telegram… without their SDKs. Nothing leaves the phone until the
 * person picks an app; the audio file itself is never shared.
 */
object SongSharer {

    /** Up to this many lyric lines fit a card legibly. */
    const val MAX_LINES = 4

    suspend fun share(context: Context, song: Song, lines: List<String> = emptyList()) {
        val file = createCard(context, song, lines)
        context.startActivity(chooser(context, uriFor(context, file), caption(song, lines)))
    }

    /** Renders the card to a fresh PNG in the cache and returns it. */
    suspend fun createCard(context: Context, song: Song, lines: List<String>): File {
        val artwork = loadArtwork(context, song)
        return withContext(Dispatchers.Default) {
            val content = ShareCardContent(
                title = song.title,
                artist = song.artist,
                album = song.album,
                artwork = artwork,
                lines = lines.map { it.trim() }.filter { it.isNotEmpty() }.take(MAX_LINES),
            )
            val bitmap = ShareCard.render(context, content)
            withContext(Dispatchers.IO) {
                val dir = File(context.cacheDir, DIR).apply { mkdirs() }
                // A new name each time: some apps cache by URI and would show the previous card.
                dir.listFiles()?.forEach { it.delete() }
                File(dir, "player-${System.currentTimeMillis()}.png").also { file ->
                    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                }
            }
        }
    }

    internal fun caption(song: Song, lines: List<String>): String {
        val credit = "${song.title} — ${song.artist}"
        val quote = lines.map { it.trim() }.filter { it.isNotEmpty() }.take(MAX_LINES)
        return if (quote.isEmpty()) credit else "“${quote.joinToString("\n")}”\n$credit"
    }

    internal fun chooser(context: Context, uri: Uri, caption: String): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, caption)
            // Lets the chooser preview the image and the chosen app read it.
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, context.getString(R.string.share_song)).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    internal fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.share", file)

    private suspend fun loadArtwork(context: Context, song: Song): Bitmap? {
        val request = ImageRequest.Builder(context)
            .data(song.artworkUri)
            .size(ARTWORK_PX)
            .allowHardware(false)
            .build()
        val result = context.imageLoader.execute(request) as? SuccessResult ?: return null
        return runCatching { result.drawable.toBitmap() }.getOrNull()
    }

    private const val DIR = "share"
    private const val ARTWORK_PX = 1000
}
