package com.danielzuniga.player

import android.app.SearchManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danielzuniga.player.remote.Pairing
import com.danielzuniga.player.remote.RemotePairing
import com.danielzuniga.player.ui.PlayerApp
import com.danielzuniga.player.ui.toast
import com.danielzuniga.player.ui.theme.PlayerTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

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
            PlayerTheme(themeMode = themeMode, dynamicColor = dynamicColor) {
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
        if (intent?.action == Intent.ACTION_VIEW) {
            RemotePairing.parse(intent.data, intent.getStringExtra("r"), intent.getStringExtra("k"))?.let(::pairBrowser)
        }
    }

    /** The camera opened a web monitor's pairing QR: hand that browser this phone's room. */
    private fun pairBrowser(temporary: Pairing) {
        val container = appContainer
        val app = applicationContext
        app.toast(R.string.web_monitor_pairing)
        // App scope: pairing takes a few seconds and must finish even if the screen rotates.
        container.appScope.launch {
            val paired = RemotePairing.pair(
                client = container.relayClient,
                relayUrl = container.relayUrl,
                temporary = temporary,
                phone = container.remote.pairingOrCreate(),
                name = Build.MODEL,
            )
            if (paired) container.remote.setEnabled(true)
            app.toast(if (paired) R.string.web_monitor_paired else R.string.web_monitor_pair_failed)
        }
    }
}
