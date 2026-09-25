package com.danielzuniga.player.widget

import android.app.Application
import android.content.Intent
import android.view.KeyEvent
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.danielzuniga.player.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAppWidgetManager

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class NowPlayingWidgetTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val widgets: ShadowAppWidgetManager =
        shadowOf(android.appwidget.AppWidgetManager.getInstance(app))

    @Test
    fun showsIdleStateBeforeAnythingPlays() {
        assertFalse(NowPlayingWidget.hasWidgets(app))
        val id = widgets.createWidget(NowPlayingWidget::class.java, R.layout.widget_now_playing)
        assertTrue(NowPlayingWidget.hasWidgets(app))

        NowPlayingWidget.push(app, NowPlayingWidget.loadState(app), null)
        assertEquals("Nada en reproducción", widgets.getViewFor(id).text(R.id.widget_title))
    }

    @Test
    fun showsSongAndSendsMediaButtons() {
        val id = widgets.createWidget(NowPlayingWidget::class.java, R.layout.widget_now_playing)
        val state = WidgetState(title = "Eres", artist = "Café Tacvba", isPlaying = true)
        NowPlayingWidget.saveState(app, state)
        NowPlayingWidget.push(app, NowPlayingWidget.loadState(app), null)

        val view = widgets.getViewFor(id)
        assertEquals("Eres", view.text(R.id.widget_title))
        assertEquals("Café Tacvba", view.text(R.id.widget_artist))
        assertEquals("Pausar", view.findViewById<ImageButton>(R.id.widget_play_pause).contentDescription)

        view.findViewById<View>(R.id.widget_next).performClick()
        val sent = shadowOf(app).broadcastIntents.last()
        assertEquals(Intent.ACTION_MEDIA_BUTTON, sent.action)
        @Suppress("DEPRECATION")
        val key = sent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
        assertEquals(KeyEvent.KEYCODE_MEDIA_NEXT, key?.keyCode)
    }

    private fun View.text(id: Int) = findViewById<TextView>(id).text.toString()
}
