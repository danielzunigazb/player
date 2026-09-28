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
    private val pair: suspend (
        temporary: Pairing,
        code: String,
        phone: () -> Pairing,
        onHandedOver: () -> Unit,
    ) -> RemotePairing.Result,
    private val onResult: (RemotePairing.Result) -> Unit,
) {
    /** A pairing waiting for the person to type [code] in the browser. */
    data class Pending(val temporary: Pairing, val code: String)

    private val _pending = MutableStateFlow<Pending?>(null)
    val pending: StateFlow<Pending?> = _pending.asStateFlow()

    private var job: Job? = null

    /** The browser already has this phone's room: nothing can take that back. */
    private var handedOver = false

    /** Starts pairing the browser waiting in [temporary], unless one is already pending. */
    fun start(temporary: Pairing): Boolean {
        if (_pending.value != null) return false
        val pending = Pending(temporary, RemoteCrypto.newPairingCode())
        _pending.value = pending
        handedOver = false
        job = scope.launch {
            finish(pair(temporary, pending.code, store::pairingOrCreate) { handedOver = true })
        }
        return true
    }

    /**
     * The person cancelled: leave the browser's room without another word. After the right code
     * the browser has the room already, so it counts as paired, and says so (the toast is how an
     * unexpected pairing gets noticed).
     */
    fun cancel() {
        val job = job ?: return
        job.cancel()
        if (handedOver) finish(RemotePairing.Result.PAIRED) else clear()
    }

    private fun finish(result: RemotePairing.Result) {
        if (result == RemotePairing.Result.PAIRED) store.setEnabled(true)
        clear()
        onResult(result)
    }

    private fun clear() {
        job = null
        handedOver = false
        _pending.value = null
    }
}
