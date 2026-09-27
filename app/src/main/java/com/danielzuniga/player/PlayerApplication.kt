package com.danielzuniga.player

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.annotation.VisibleForTesting
import com.danielzuniga.player.data.MusicRepository
import com.danielzuniga.player.data.SettingsStore
import com.danielzuniga.player.data.UserDataRepository
import com.danielzuniga.player.data.db.AppDatabase
import com.danielzuniga.player.data.lyrics.LrcLibClient
import com.danielzuniga.player.data.lyrics.LyricsRepository
import com.danielzuniga.player.data.tags.TagFixRepository
import com.danielzuniga.player.data.update.UpdateChecker
import com.danielzuniga.player.data.update.UpdateRepository
import com.danielzuniga.player.playback.AudioEffects
import com.danielzuniga.player.playback.PlaybackStateStore
import com.danielzuniga.player.remote.BrowserPairing
import com.danielzuniga.player.remote.RemotePairing
import com.danielzuniga.player.remote.RemoteStore
import com.danielzuniga.player.ui.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** App-wide singletons shared by the UI and the playback service (same process). */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val settings = SettingsStore(context)
    private val database = AppDatabase.create(context)
    private val userAgent = "Player/${BuildConfig.VERSION_NAME} (https://github.com/danielzunigazb/player)"
    private val lrcLib = LrcLibClient(userAgent = userAgent)
    val tagFixes = TagFixRepository(
        dao = database.tagFixDao(),
        client = lrcLib,
        onlineEnabled = { settings.onlineTags.value },
        scope = appScope,
    )
    val musicRepository = MusicRepository(context, settings, tagFixes, appScope)
    val userData = UserDataRepository(database)
    val playbackState = PlaybackStateStore(context)
    val audioEffects = AudioEffects(context)
    val updates = UpdateRepository(
        context = context,
        currentVersion = BuildConfig.VERSION_NAME,
        checker = UpdateChecker(userAgent),
        autoCheck = { settings.autoUpdates.value },
        scope = appScope,
    )
    val remote = RemoteStore(context)
    var relayUrl = BuildConfig.RELAY_URL
        // Tests point it at a stand-in relay.
        @VisibleForTesting internal set
    /** WebSockets to the web monitor's relay; the ping notices a dead connection within a minute. */
    val relayClient: OkHttpClient by lazy { OkHttpClient.Builder().pingInterval(25, TimeUnit.SECONDS).build() }
    /** The web monitor pairing the phone is showing a code for, if any. */
    val browserPairing = BrowserPairing(
        scope = appScope,
        store = remote,
        pair = { temporary, code, phone, onHandedOver ->
            RemotePairing.pair(relayClient, relayUrl, temporary, code, phone, Build.MODEL, onHandedOver)
        },
        onResult = { result ->
            appContext.toast(
                when (result) {
                    RemotePairing.Result.PAIRED -> R.string.web_monitor_paired
                    RemotePairing.Result.WRONG_CODE -> R.string.web_monitor_pair_wrong_code
                    RemotePairing.Result.TIMED_OUT -> R.string.web_monitor_pair_failed
                },
            )
        },
    )
    val lyrics = LyricsRepository(
        context = context,
        onlineEnabled = { settings.onlineLyrics.value },
        client = lrcLib,
    )
}

class PlayerApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as PlayerApplication).container
