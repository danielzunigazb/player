package com.danielzuniga.player

import android.app.SearchManager
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danielzuniga.player.ui.PlayerApp
import com.danielzuniga.player.ui.theme.PlayerTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    /** Pending "play X" request from voice assistants or Android Auto. */
    private val searchRequest = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        val settings = appContainer.settings
        setContent {
            val themeMode by settings.themeMode.collectAsStateWithLifecycle()
            val dynamicColor by settings.dynamicColor.collectAsStateWithLifecycle()
            val artworkColors by settings.artworkColors.collectAsStateWithLifecycle()
            val search by searchRequest.collectAsStateWithLifecycle()
            PlayerTheme(themeMode = themeMode, dynamicColor = dynamicColor, artworkColors = artworkColors) {
                PlayerApp(searchRequest = search, onSearchHandled = { searchRequest.value = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH) {
            searchRequest.value = intent.getStringExtra(SearchManager.QUERY).orEmpty()
        }
    }
}
