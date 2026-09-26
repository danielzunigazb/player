package com.danielzuniga.player.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danielzuniga.player.BuildConfig
import com.danielzuniga.player.R
import com.danielzuniga.player.appContainer

/**
 * Offers a newer version when the update check finds one. "Download" opens the APK link in the
 * browser, which downloads it; Android then installs it over this one, and only if it's signed
 * with the same key.
 */
@Composable
fun UpdateOffer() {
    val context = LocalContext.current
    val updates = context.appContainer.updates
    val release by updates.offer.collectAsStateWithLifecycle()
    val offer = release ?: return

    AlertDialog(
        onDismissRequest = { updates.dismiss(offer) },
        title = { Text(stringResource(R.string.update_available, offer.version)) },
        text = { Text(stringResource(R.string.update_available_body, BuildConfig.VERSION_NAME)) },
        confirmButton = {
            TextButton(onClick = {
                updates.dismiss(offer)
                context.openLink(offer.apkUrl)
            }) { Text(stringResource(R.string.update_download)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { context.openLink(offer.pageUrl) }) { Text(stringResource(R.string.update_notes)) }
                TextButton(onClick = { updates.dismiss(offer) }) { Text(stringResource(R.string.update_later)) }
            }
        },
    )
}

private fun Context.openLink(url: String) {
    if (url.isBlank()) return
    try {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // No browser: nothing sensible to do.
    }
}
