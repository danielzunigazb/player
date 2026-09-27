package com.danielzuniga.player.remote

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The phone's room and key, shared by every browser paired with it. */
data class Pairing(val room: String, val key: String)

/**
 * Whether the web monitor is on, and this phone's room. The room exists from the first pairing;
 * [unpairAll] replaces it, so browsers paired before are left talking to nobody.
 */
class RemoteStore(context: Context) {

    private val prefs = context.getSharedPreferences("remote", Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _pairing = MutableStateFlow(read())
    val pairing: StateFlow<Pairing?> = _pairing.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_ENABLED, enabled) }
        _enabled.value = enabled
    }

    /** This phone's room, created on first use. */
    fun pairingOrCreate(): Pairing = _pairing.value ?: create()

    fun unpairAll() {
        prefs.edit { remove(KEY_ROOM).remove(KEY_KEY) }
        _pairing.value = null
    }

    private fun create(): Pairing {
        val pairing = Pairing(RemoteCrypto.newRoom(), RemoteCrypto.newKey())
        prefs.edit { putString(KEY_ROOM, pairing.room).putString(KEY_KEY, pairing.key) }
        _pairing.value = pairing
        return pairing
    }

    private fun read(): Pairing? {
        val room = prefs.getString(KEY_ROOM, null) ?: return null
        val key = prefs.getString(KEY_KEY, null) ?: return null
        return Pairing(room, key).takeIf { RemoteCrypto.isRoom(room) && RemoteCrypto.isKey(key) }
    }

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_ROOM = "room"
        const val KEY_KEY = "key"
    }
}
