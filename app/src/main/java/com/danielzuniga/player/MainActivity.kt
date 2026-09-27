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
import com.danielzuniga.player.remote.RemoteCrypto
import com.danielzuniga.player.remote.RemotePairing
import com.danielzuniga.player.ui.PairBrowserDialog
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

    /** A web monitor's pairing link, waiting for the person to accept or cancel it. */
    private val pairRequest = MutableStateFlow<Pairing?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent) else restorePairRequest(savedInstanceState)
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
            val pairing by pairRequest.collectAsStateWithLifecycle()
            PlayerTheme(themeMode = themeMode, dynamicColor = dynamicColor) {
                PlayerApp(searchRequest = search, onSearchHandled = { searchRequest.value = null })
                pairing?.let { temporary ->
                    PairBrowserDialog(
                        code = RemoteCrypto.pairingCode(temporary.key),
                        onAccept = {
                            pairRequest.value = null
                            pairBrowser(temporary)
                        },
                        onDismiss = { pairRequest.value = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pairRequest.value?.let {
            outState.putString(STATE_PAIR_ROOM, it.room)
            outState.putString(STATE_PAIR_KEY, it.key)
        }
    }

    /** A rotation keeps the pairing question open. */
    private fun restorePairRequest(state: Bundle) {
        val room = state.getString(STATE_PAIR_ROOM) ?: return
        val key = state.getString(STATE_PAIR_KEY) ?: return
        if (RemoteCrypto.isRoom(room) && RemoteCrypto.isKey(key)) pairRequest.value = Pairing(room, key)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH) {
            searchRequest.value = intent.getStringExtra(SearchManager.QUERY).orEmpty()
        }
        if (intent?.action == Intent.ACTION_VIEW) {
            // Asked first (PairBrowserDialog): any page or message can hand the phone such a
            // link, and the browser behind it would get this phone's room.
            RemotePairing.parse(intent.data, intent.getStringExtra("r"), intent.getStringExtra("k"))
                ?.let { pairRequest.value = it }
        }
    }

    /** The person accepted a web monitor's pairing link: hand that browser this phone's room. */
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

    private companion object {
        const val STATE_PAIR_ROOM = "pair_room"
        const val STATE_PAIR_KEY = "pair_key"
    }
}
