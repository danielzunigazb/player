package com.danielzuniga.player.data.update

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: Release) : UpdateState
    data object Failed : UpdateState
}

/**
 * Keeps track of whether a newer Player is out. [checkIfDue] runs at most once a day when the
 * automatic check is on; [checkNow] is the button in Settings. An offer the user declined isn't
 * shown again for that version ([dismiss]), but a manual check always shows what it finds.
 */
class UpdateRepository(
    context: Context,
    private val currentVersion: String,
    private val checker: UpdateChecker,
    private val autoCheck: () -> Boolean,
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** A version to offer right now, unless the user already said "not now" to it. */
    private val _offer = MutableStateFlow<Release?>(null)
    val offer: StateFlow<Release?> = _offer.asStateFlow()

    /**
     * A check is in flight; cleared before its result is published, so a check asked for right
     * after seeing that result always runs. Everything here runs on the main thread.
     */
    private var checking = false

    fun checkIfDue() {
        if (!autoCheck()) return
        if (now() - prefs.getLong(KEY_LAST_CHECK, 0L) < DAY_MS) return
        check(manual = false)
    }

    fun checkNow() = check(manual = true)

    /** "Not now": don't offer [release] again on its own. */
    fun dismiss(release: Release) {
        prefs.edit { putString(KEY_DISMISSED, release.version) }
        _offer.value = null
    }

    private fun check(manual: Boolean) {
        if (checking) return
        checking = true
        scope.launch {
            _state.value = UpdateState.Checking
            val latest = try {
                withContext(Dispatchers.IO) { checker.latest() }
            } finally {
                checking = false
            }
            if (latest == null) {
                _state.value = UpdateState.Failed
                return@launch
            }
            prefs.edit { putLong(KEY_LAST_CHECK, now()) }
            if (UpdateChecker.isNewer(latest.version, currentVersion)) {
                _state.value = UpdateState.Available(latest)
                if (manual || prefs.getString(KEY_DISMISSED, null) != latest.version) _offer.value = latest
            } else {
                _state.value = UpdateState.UpToDate
            }
        }
    }

    private companion object {
        const val KEY_LAST_CHECK = "last_check"
        const val KEY_DISMISSED = "dismissed_version"
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}
