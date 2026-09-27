package com.danielzuniga.player.remote

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The one browser pairing in progress (docs/monitor.md), app-wide so that a rotation keeps it
 * and its code. While [pending] is set the phone shows the code, and other pairing links are
 * ignored: swapping them would change the browser behind the code on screen.
 */
class BrowserPairing(
    private val scope: CoroutineScope,
    private val store: RemoteStore,
    private val pair: suspend (temporary: Pairing, code: String, phone: () -> Pairing) -> RemotePairing.Result,
    private val onResult: (RemotePairing.Result) -> Unit,
) {
    /** A pairing waiting for the person to type [code] in the browser. */
    data class Pending(val temporary: Pairing, val code: String)

    private val _pending = MutableStateFlow<Pending?>(null)
    val pending: StateFlow<Pending?> = _pending.asStateFlow()

    private var job: Job? = null

    /** Starts pairing the browser waiting in [temporary], unless one is already pending. */
    fun start(temporary: Pairing): Boolean {
        if (_pending.value != null) return false
        val pending = Pending(temporary, RemoteCrypto.newPairingCode())
        _pending.value = pending
        job = scope.launch {
            val result = pair(temporary, pending.code, store::pairingOrCreate)
            if (result == RemotePairing.Result.PAIRED) store.setEnabled(true)
            _pending.value = null
            job = null
            onResult(result)
        }
        return true
    }

    /** The person cancelled: leave the browser's room without another word. */
    fun cancel() {
        job?.cancel()
        job = null
        _pending.value = null
    }
}
