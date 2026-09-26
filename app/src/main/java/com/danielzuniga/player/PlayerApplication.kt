package com.danielzuniga.player

import android.app.Application
import android.content.Context
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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
