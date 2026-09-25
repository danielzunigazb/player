package com.danielzuniga.player

import android.app.Application
import android.content.Context
import com.danielzuniga.player.data.MusicRepository
import com.danielzuniga.player.data.SettingsStore
import com.danielzuniga.player.data.UserDataRepository
import com.danielzuniga.player.data.db.AppDatabase
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
    val musicRepository = MusicRepository(context, settings, appScope)
    val userData = UserDataRepository(AppDatabase.create(context))
    val playbackState = PlaybackStateStore(context)
    val audioEffects = AudioEffects(context)
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
