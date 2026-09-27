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
import androidx.compose.ui.window.DialogProperties
import com.danielzuniga.player.R

/**
 * Shown while a web monitor pairs: the [code] the person types in the browser on their computer.
 * Only that browser gets this phone's room, and only once the code is right; whoever crafted a
 * pairing link never sees this screen. Stays up while waiting (a tap outside doesn't close it).
 */
@Composable
fun PairBrowserDialog(code: String, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(stringResource(R.string.web_monitor_pair_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.web_monitor_pair_body))
                Text(
                    code,
                    style = MaterialTheme.typography.displaySmall.copy(letterSpacing = 0.2.em),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
        },
    )
}
