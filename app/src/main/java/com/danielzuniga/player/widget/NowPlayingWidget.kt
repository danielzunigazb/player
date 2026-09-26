package com.danielzuniga.player.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.KeyEvent
import android.widget.RemoteViews
import androidx.annotation.OptIn
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaButtonReceiver
import com.danielzuniga.player.MainActivity
import com.danielzuniga.player.R
import kotlin.concurrent.thread

data class WidgetState(
    val title: String? = null,
    val artist: String? = null,
    val isPlaying: Boolean = false,
    val artworkUri: Uri? = null,
)

/**
 * Home-screen widget with the current song and transport buttons. Buttons send media-button
 * events through Media3's MediaButtonReceiver, so play/pause also resumes the last queue
 * when the app isn't running.
 */
class NowPlayingWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val state = loadState(context)
        // Null when onReceive() is invoked directly rather than through a real broadcast.
        val pending: PendingResult? = goAsync()
        runInBackground {
            try {
                push(context, state, loadArtwork(context, state.artworkUri))
            } finally {
                pending?.finish()
            }
        }
    }

    companion object {
        private const val PREFS = "widget_state"

        /** Where [onUpdate] decodes artwork; tests swap it for an inline runner to avoid races. */
        @VisibleForTesting
        internal var runInBackground: (() -> Unit) -> Unit = { block -> thread(block = block) }
        private const val ART_SIZE_PX = 192

        fun hasWidgets(context: Context): Boolean =
            AppWidgetManager.getInstance(context).getAppWidgetIds(component(context)).isNotEmpty()

        fun push(context: Context, state: WidgetState, artwork: Bitmap?) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(component(context))
            if (ids.isEmpty()) return
            manager.updateAppWidget(ids, views(context, state, artwork))
        }

        fun saveState(context: Context, state: WidgetState) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
                putString("title", state.title)
                putString("artist", state.artist)
                putBoolean("playing", state.isPlaying)
                putString("art", state.artworkUri?.toString())
            }
        }

        fun loadState(context: Context): WidgetState {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return WidgetState(
                title = prefs.getString("title", null),
                artist = prefs.getString("artist", null),
                isPlaying = prefs.getBoolean("playing", false),
                artworkUri = prefs.getString("art", null)?.toUri(),
            )
        }

        /** Decodes a small copy of the cover; launchers can't read our content URIs directly. */
        fun loadArtwork(context: Context, uri: Uri?): Bitmap? {
            if (uri == null) return null
            return runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= ART_SIZE_PX && bounds.outHeight / (sample * 2) >= ART_SIZE_PX) {
                    sample *= 2
                }
                val options = BitmapFactory.Options().apply { inSampleSize = sample }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            }.getOrNull()
        }

        internal fun views(context: Context, state: WidgetState, artwork: Bitmap?): RemoteViews =
            RemoteViews(context.packageName, R.layout.widget_now_playing).apply {
                setTextViewText(R.id.widget_title, state.title ?: context.getString(R.string.widget_idle))
                setTextViewText(R.id.widget_artist, state.artist.orEmpty())
                if (artwork != null) {
                    setImageViewBitmap(R.id.widget_art, artwork)
                } else {
                    setImageViewResource(R.id.widget_art, R.drawable.ic_launcher_foreground)
                }
                setImageViewResource(
                    R.id.widget_play_pause,
                    if (state.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
                )
                setContentDescription(
                    R.id.widget_play_pause,
                    context.getString(if (state.isPlaying) R.string.pause else R.string.play),
                )
                setOnClickPendingIntent(R.id.widget_previous, mediaButton(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS))
                setOnClickPendingIntent(R.id.widget_play_pause, mediaButton(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
                setOnClickPendingIntent(R.id.widget_next, mediaButton(context, KeyEvent.KEYCODE_MEDIA_NEXT))
                setOnClickPendingIntent(
                    R.id.widget_root,
                    PendingIntent.getActivity(
                        context,
                        0,
                        Intent(context, MainActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                )
            }

        @OptIn(UnstableApi::class)
        private fun mediaButton(context: Context, keyCode: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                keyCode,
                Intent(Intent.ACTION_MEDIA_BUTTON)
                    .setComponent(ComponentName(context, MediaButtonReceiver::class.java))
                    .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode)),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        private fun component(context: Context) = ComponentName(context, NowPlayingWidget::class.java)
    }
}
