package com.danielzuniga.player.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.danielzuniga.player.R

/**
 * Asks before handing this phone's room to a browser: a pairing link can come from anyone, and
 * the browser that gets the room controls Player from then on. [code] is the one the web
 * monitor shows under its QR (RemoteCrypto.pairingCode), so the person can tell it's theirs.
 */
@Composable
fun PairBrowserDialog(code: String, onAccept: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.web_monitor_pair_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.web_monitor_pair_body))
                Text(
                    code,
                    style = MaterialTheme.typography.headlineMedium.copy(letterSpacing = 0.2.em),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onAccept) { Text(stringResource(R.string.web_monitor_pair_accept)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
