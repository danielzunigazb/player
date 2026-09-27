package com.danielzuniga.player

import android.app.SearchManager
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danielzuniga.player.remote.RemotePairing
import com.danielzuniga.player.ui.PairBrowserDialog
import com.danielzuniga.player.ui.PlayerApp
import com.danielzuniga.player.ui.theme.PlayerTheme
import kotlinx.coroutines.flow.MutableStateFlow

// AppCompatActivity (not plain ComponentActivity) so the in-app language applies on Android 12
// and older too; see AppLanguage.
class MainActivity : AppCompatActivity() {

    /** Pending "play X" request from voice assistants or Android Auto. */
    private val searchRequest = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        val settings = appContainer.settings
        val browserPairing = appContainer.browserPairing
        // At most once a day, and only if the automatic check is on.
        appContainer.updates.checkIfDue()
        appContainer.musicRepository.setUnknownLabels(
            getString(R.string.unknown_artist),
            getString(R.string.unknown_album),
        )
        setContent {
            val themeMode by settings.themeMode.collectAsStateWithLifecycle()
            val dynamicColor by settings.dynamicColor.collectAsStateWithLifecycle()
            val search by searchRequest.collectAsStateWithLifecycle()
            val pairing by browserPairing.pending.collectAsStateWithLifecycle()
            PlayerTheme(themeMode = themeMode, dynamicColor = dynamicColor) {
                PlayerApp(searchRequest = search, onSearchHandled = { searchRequest.value = null })
                pairing?.let { PairBrowserDialog(code = it.code, onCancel = browserPairing::cancel) }
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
        if (intent?.action == Intent.ACTION_VIEW) {
            // A web monitor's pairing QR: the phone shows a code to type in that browser
            // (PairBrowserDialog), and hands over its room only once it's typed. A link that
            // arrives while one is pending is ignored (BrowserPairing).
            RemotePairing.parse(intent.data, intent.getStringExtra("r"), intent.getStringExtra("k"))
                ?.let(appContainer.browserPairing::start)
        }
    }
}
