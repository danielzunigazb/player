package com.danielzuniga.player.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danielzuniga.player.R
import com.danielzuniga.player.ui.library.LibraryScreen
import com.danielzuniga.player.ui.player.MiniPlayer
import com.danielzuniga.player.ui.player.NowPlayingScreen

private val AUDIO_PERMISSION =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

@Composable
fun PlayerApp(viewModel: MusicViewModel) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(context.hasAudioPermission()) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasPermission = granted }

    // Picks up a permission granted from system settings while the app was in the background.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasPermission = context.hasAudioPermission()
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) viewModel.loadLibrary()
    }

    if (!hasPermission) {
        PermissionScreen(
            onRequest = { permissionLauncher.launch(AUDIO_PERMISSION) },
            onOpenSettings = { context.openAppSettings() },
        )
        return
    }

    val library by viewModel.libraryState.collectAsStateWithLifecycle()
    val player by viewModel.playerState.collectAsStateWithLifecycle()
    var showNowPlaying by rememberSaveable { mutableStateOf(false) }
    val nowPlaying = player.nowPlaying

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                if (nowPlaying != null) {
                    MiniPlayer(
                        state = player,
                        nowPlaying = nowPlaying,
                        onClick = { showNowPlaying = true },
                        onTogglePlay = viewModel::togglePlayPause,
                        onNext = viewModel::next,
                    )
                }
            },
        ) { innerPadding ->
            LibraryScreen(
                state = library,
                onQueryChange = viewModel::onQueryChange,
                onSongClick = viewModel::playFromList,
                onShuffleAll = viewModel::shuffleAll,
                onRefresh = { viewModel.loadLibrary(force = true) },
                contentPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding()),
                modifier = Modifier.padding(top = innerPadding.calculateTopPadding()),
            )
        }

        AnimatedVisibility(
            visible = showNowPlaying && nowPlaying != null,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            if (nowPlaying != null) {
                NowPlayingScreen(
                    state = player,
                    nowPlaying = nowPlaying,
                    onClose = { showNowPlaying = false },
                    onTogglePlay = viewModel::togglePlayPause,
                    onNext = viewModel::next,
                    onPrevious = viewModel::previous,
                    onSeek = viewModel::seekTo,
                    onToggleShuffle = viewModel::toggleShuffle,
                    onCycleRepeat = viewModel::cycleRepeatMode,
                )
            }
        }
    }

    BackHandler(enabled = showNowPlaying) { showNowPlaying = false }
}

@Composable
private fun PermissionScreen(onRequest: () -> Unit, onOpenSettings: () -> Unit) {
    Scaffold { innerPadding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(32.dp),
        ) {
            Icon(
                Icons.Rounded.LibraryMusic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(72.dp),
            )
            Text(
                text = stringResource(R.string.permission_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(R.string.permission_body),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onRequest) { Text(stringResource(R.string.permission_grant)) }
            TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.permission_settings)) }
        }
    }
}

private fun Context.hasAudioPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, AUDIO_PERMISSION) == PackageManager.PERMISSION_GRANTED

private fun Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
