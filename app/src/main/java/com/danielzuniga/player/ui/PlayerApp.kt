package com.danielzuniga.player.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.danielzuniga.player.R
import com.danielzuniga.player.data.LibraryIndex
import com.danielzuniga.player.data.lyrics.Lyrics
import com.danielzuniga.player.playback.PlayerUiState
import com.danielzuniga.player.ui.terminal.Shell
import com.danielzuniga.player.ui.terminal.ShellHost
import com.danielzuniga.player.ui.terminal.ShellLine
import com.danielzuniga.player.ui.terminal.ShellText
import com.danielzuniga.player.ui.terminal.TerminalScreen
import com.danielzuniga.player.ui.components.DzButton
import com.danielzuniga.player.ui.components.DzButtonVariant
import com.danielzuniga.player.ui.components.DzMark
import com.danielzuniga.player.ui.components.DzTitle
import com.danielzuniga.player.ui.theme.Dz
import com.danielzuniga.player.ui.theme.DzType
import com.danielzuniga.player.data.Song
import com.danielzuniga.player.ui.components.LocalCurrentSongId
import com.danielzuniga.player.ui.components.LocalFavoriteIds
import com.danielzuniga.player.ui.components.LocalIsPlaying
import com.danielzuniga.player.ui.components.LocalSongActions
import com.danielzuniga.player.ui.components.SongActions
import com.danielzuniga.player.ui.detail.AlbumScreen
import com.danielzuniga.player.ui.detail.ArtistScreen
import com.danielzuniga.player.ui.detail.FolderScreen
import com.danielzuniga.player.ui.equalizer.EqualizerScreen
import com.danielzuniga.player.ui.library.HomeScreen
import com.danielzuniga.player.ui.player.MiniPlayer
import com.danielzuniga.player.ui.player.NowPlayingActions
import com.danielzuniga.player.ui.player.NowPlayingScreen
import com.danielzuniga.player.ui.player.QueueSheet
import com.danielzuniga.player.ui.player.SleepTimerSheet
import com.danielzuniga.player.ui.player.SpeedSheet
import com.danielzuniga.player.ui.playlists.AddToPlaylistDialog
import com.danielzuniga.player.ui.playlists.PlaylistDetailScreen
import com.danielzuniga.player.ui.playlists.PlaylistNameDialog
import com.danielzuniga.player.ui.settings.AppLanguage
import com.danielzuniga.player.ui.settings.SettingsScreen
import com.danielzuniga.player.ui.theme.DzIcons

private val AUDIO_PERMISSION =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

private enum class PlayerSheet { QUEUE, SLEEP, SPEED }

@Composable
fun PlayerApp(searchRequest: String? = null, onSearchHandled: () -> Unit = {}) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(context.hasAudioPermission()) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasPermission = granted }

    // Picks up a permission granted from system settings while the app was in the background.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasPermission = context.hasAudioPermission()
    }

    if (!hasPermission) {
        PermissionScreen(
            onRequest = { permissionLauncher.launch(AUDIO_PERMISSION) },
            onOpenSettings = { context.openAppSettings() },
        )
    } else {
        MainContent(searchRequest, onSearchHandled)
    }
}

@Composable
private fun MainContent(searchRequest: String?, onSearchHandled: () -> Unit) {
    val context = LocalContext.current
    val playerVm: PlayerViewModel = viewModel(factory = AppViewModels.Factory)
    val libraryVm: LibraryViewModel = viewModel(factory = AppViewModels.Factory)
    val playlistsVm: PlaylistsViewModel = viewModel(factory = AppViewModels.Factory)

    LaunchedEffect(Unit) { libraryVm.load() }
    LaunchedEffect(searchRequest) {
        if (searchRequest != null) {
            playerVm.playFromSearch(searchRequest)
            onSearchHandled()
        }
    }

    val player by playerVm.state.collectAsStateWithLifecycle()
    val queue by playerVm.queue.collectAsStateWithLifecycle()
    val favoriteIds by playerVm.favoriteIds.collectAsStateWithLifecycle()
    val lyrics by playerVm.lyrics.collectAsStateWithLifecycle()
    val libraryState by libraryVm.state.collectAsStateWithLifecycle()
    val libraryIndex by libraryVm.library.collectAsStateWithLifecycle()
    val playlistsState by playlistsVm.state.collectAsStateWithLifecycle()

    val navController = rememberNavController()
    var showNowPlaying by rememberSaveable { mutableStateOf(false) }
    var showTerminal by rememberSaveable { mutableStateOf(false) }
    val terminalLines = remember { mutableStateListOf<ShellLine>() }
    val mostPlayedIds by playerVm.mostPlayedIds.collectAsStateWithLifecycle()
    var sheet by rememberSaveable { mutableStateOf<PlayerSheet?>(null) }
    var addToPlaylistSongs by remember { mutableStateOf<List<Song>?>(null) }
    var newPlaylistSongs by remember { mutableStateOf<List<Song>?>(null) }

    fun navigate(route: String) {
        showNowPlaying = false
        navController.navigate(route) { launchSingleTop = true }
    }

    val songActions = remember {
        SongActions(
            play = playerVm::play,
            shuffle = playerVm::shuffle,
            playNext = { songs ->
                playerVm.playNext(songs)
                context.toast(R.string.queued_next)
            },
            addToQueue = { songs ->
                playerVm.addToQueue(songs)
                context.toast(R.string.queued)
            },
            addToPlaylist = { songs -> addToPlaylistSongs = songs },
            toggleFavorite = { song -> playerVm.toggleFavorite(song.id) },
            openAlbum = { id -> navigate(Routes.album(id)) },
            openArtist = { name -> navigate(Routes.artist(name)) },
        )
    }

    val nowPlaying = player.nowPlaying
    val currentSong = nowPlaying?.songId?.let(libraryIndex::song)

    CompositionLocalProvider(
        LocalSongActions provides songActions,
        LocalFavoriteIds provides favoriteIds,
        LocalCurrentSongId provides nowPlaying?.songId,
        LocalIsPlaying provides player.isPlaying,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                contentWindowInsets = WindowInsets(0),
                bottomBar = {
                    if (nowPlaying != null) {
                        MiniPlayer(
                            state = player,
                            nowPlaying = nowPlaying,
                            onClick = { showNowPlaying = true },
                            onTogglePlay = playerVm::togglePlayPause,
                            onNext = playerVm::next,
                            onPrevious = playerVm::previous,
                        )
                    } else {
                        Spacer(Modifier.navigationBarsPadding())
                    }
                },
            ) { innerPadding ->
                val bottomPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding())
                AppNavHost(
                    navController = navController,
                    bottomPadding = bottomPadding,
                    libraryState = libraryState,
                    playlistsState = playlistsState,
                    libraryVm = libraryVm,
                    onCreatePlaylist = { newPlaylistSongs = emptyList() },
                    onOpenTerminal = { showTerminal = true },
                    navigate = ::navigate,
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
                        isFavorite = nowPlaying.songId in favoriteIds,
                        lyrics = lyrics,
                        actions = NowPlayingActions(
                            onClose = { showNowPlaying = false },
                            onTogglePlay = playerVm::togglePlayPause,
                            onNext = playerVm::next,
                            onPrevious = playerVm::previous,
                            onSeek = playerVm::seekTo,
                            onToggleShuffle = playerVm::toggleShuffle,
                            onCycleRepeat = playerVm::cycleRepeatMode,
                            onToggleFavorite = { nowPlaying.songId?.let(playerVm::toggleFavorite) },
                            onOpenQueue = { sheet = PlayerSheet.QUEUE },
                            onOpenSleepTimer = { sheet = PlayerSheet.SLEEP },
                            onOpenSpeed = { sheet = PlayerSheet.SPEED },
                            onOpenEqualizer = { navigate(Routes.EQUALIZER) },
                            onAddToPlaylist = { currentSong?.let { addToPlaylistSongs = listOf(it) } },
                            onGoToAlbum = { currentSong?.let { navigate(Routes.album(it.albumId)) } },
                            onGoToArtist = { currentSong?.let { navigate(Routes.artist(it.artists.first())) } },
                        ),
                    )
                }
            }
        }

        // The shell reads the latest state on every command, so it's built once.
        val shellState = rememberUpdatedState(ShellSnapshot(player, libraryIndex, favoriteIds, mostPlayedIds, lyrics))
        // Keyed on the language: switching it in Settings rebuilds the shell in the new one.
        val language = LocalConfiguration.current.locales[0].language
        val shell = remember(language) {
            Shell(
                object : ShellHost {
                    override val library get() = shellState.value.library
                    override val player get() = shellState.value.player
                    override val favoriteIds get() = shellState.value.favoriteIds
                    override val mostPlayed get() = shellState.value.library.songs(shellState.value.mostPlayedIds)
                    override val currentLyric: String?
                        get() {
                            val synced = shellState.value.lyrics.lyrics as? Lyrics.Synced ?: return null
                            return synced.lines.getOrNull(synced.indexAt(shellState.value.player.positionMs))?.text
                        }

                    override fun play(songs: List<Song>, index: Int) = playerVm.play(songs, index)
                    override fun shuffle(songs: List<Song>) = playerVm.shuffle(songs)
                    override fun playNext(songs: List<Song>) = playerVm.playNext(songs)
                    override fun addToQueue(songs: List<Song>) = playerVm.addToQueue(songs)
                    override fun togglePlay() = playerVm.togglePlayPause()
                    override fun skip() = playerVm.next()
                    override fun previous() = playerVm.previous()
                    override fun seek(positionMs: Long) = playerVm.seekTo(positionMs)
                    override fun toggleShuffle() = playerVm.toggleShuffle()
                    override fun cycleRepeat() = playerVm.cycleRepeatMode()
                    override fun toggleFavorite(songId: Long) = playerVm.toggleFavorite(songId)
                    override fun setSleep(minutes: Int) = playerVm.setSleepTimer(minutes)
                    override fun setSpeed(speed: Float) = playerVm.setSpeed(speed)
                },
                ShellText.forLanguage(language),
            )
        }
        AnimatedVisibility(
            visible = showTerminal,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            TerminalScreen(shell = shell, lines = terminalLines, onClose = { showTerminal = false })
        }

        BackHandler(enabled = showTerminal) { showTerminal = false }
        BackHandler(enabled = showNowPlaying) { showNowPlaying = false }

        when (sheet) {
            PlayerSheet.QUEUE -> QueueSheet(
                queue = queue,
                onDismiss = { sheet = null },
                onSkipTo = playerVm::skipToQueueItem,
                onRemove = playerVm::removeQueueItem,
                onMove = playerVm::moveQueueItem,
            )
            PlayerSheet.SLEEP -> SleepTimerSheet(
                state = player,
                onSet = playerVm::setSleepTimer,
                onDismiss = { sheet = null },
            )
            PlayerSheet.SPEED -> SpeedSheet(
                current = player.playbackSpeed,
                onSet = playerVm::setSpeed,
                onDismiss = { sheet = null },
            )
            null -> Unit
        }

        addToPlaylistSongs?.let { songs ->
            AddToPlaylistDialog(
                playlists = playlistsState.playlists,
                onSelect = { playlist ->
                    playlistsVm.addTo(playlist.id, songs)
                    context.toast(context.getString(R.string.added_to_playlist, playlist.name))
                    addToPlaylistSongs = null
                },
                onCreateNew = {
                    newPlaylistSongs = songs
                    addToPlaylistSongs = null
                },
                onDismiss = { addToPlaylistSongs = null },
            )
        }

        newPlaylistSongs?.let { songs ->
            PlaylistNameDialog(
                title = stringResource(R.string.new_playlist),
                confirmLabel = stringResource(R.string.create),
                onConfirm = { name ->
                    playlistsVm.create(name, songs) { id -> navigate(Routes.playlist(id)) }
                    newPlaylistSongs = null
                },
                onDismiss = { newPlaylistSongs = null },
            )
        }
    }
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    bottomPadding: PaddingValues,
    libraryState: LibraryUiState,
    playlistsState: PlaylistsUiState,
    libraryVm: LibraryViewModel,
    onCreatePlaylist: () -> Unit,
    onOpenTerminal: () -> Unit,
    navigate: (String) -> Unit,
) {
    val libraryIndex by libraryVm.library.collectAsStateWithLifecycle()

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                library = libraryState,
                playlists = playlistsState,
                onQueryChange = libraryVm::onQueryChange,
                onSortChange = libraryVm::setSort,
                onRefresh = { libraryVm.load(force = true) },
                onOpenAlbum = { navigate(Routes.album(it)) },
                onOpenArtist = { navigate(Routes.artist(it)) },
                onOpenFolder = { navigate(Routes.folder(it)) },
                onOpenPlaylist = { navigate(Routes.playlist(it)) },
                onOpenSmartPlaylist = { navigate(Routes.smartPlaylist(it)) },
                onCreatePlaylist = onCreatePlaylist,
                onOpenSettings = { navigate(Routes.SETTINGS) },
                contentPadding = bottomPadding,
                onOpenTerminal = onOpenTerminal,
            )
        }
        composable(
            Routes.ALBUM,
            arguments = listOf(navArgument(Routes.ARG_ID) { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments?.getLong(Routes.ARG_ID) ?: 0L
            AlbumScreen(libraryIndex.album(id), navController::popBackStack, bottomPadding)
        }
        composable(
            Routes.ARTIST,
            arguments = listOf(navArgument(Routes.ARG_NAME) { type = NavType.StringType }),
        ) { entry ->
            val name = entry.arguments?.getString(Routes.ARG_NAME).orEmpty()
            ArtistScreen(libraryIndex.artist(name), navController::popBackStack, bottomPadding)
        }
        composable(
            Routes.FOLDER,
            arguments = listOf(navArgument(Routes.ARG_PATH) { type = NavType.StringType }),
        ) { entry ->
            val path = entry.arguments?.getString(Routes.ARG_PATH).orEmpty()
            FolderScreen(libraryIndex.folder(path), navController::popBackStack, bottomPadding)
        }
        composable(
            Routes.PLAYLIST,
            arguments = listOf(
                navArgument(Routes.ARG_KIND) { type = NavType.StringType },
                navArgument(Routes.ARG_ID) { type = NavType.LongType },
            ),
        ) {
            val vm: PlaylistDetailViewModel = viewModel(factory = AppViewModels.Factory)
            val state by vm.state.collectAsStateWithLifecycle()
            PlaylistDetailScreen(
                state = state,
                onBack = navController::popBackStack,
                onRename = vm::rename,
                onDelete = {
                    vm.delete()
                    navController.popBackStack()
                },
                onRemoveSong = vm::remove,
                onReorder = vm::saveOrder,
                bottomPadding = bottomPadding,
            )
        }
        composable(Routes.EQUALIZER) {
            val vm: EqualizerViewModel = viewModel(factory = AppViewModels.Factory)
            val state by vm.state.collectAsStateWithLifecycle()
            EqualizerScreen(
                state = state,
                onBack = navController::popBackStack,
                onEnabledChange = vm::setEnabled,
                onPreset = vm::usePreset,
                onBandLevel = vm::setBandLevel,
                onBassStrength = vm::setBassStrength,
                bottomPadding = bottomPadding,
            )
        }
        composable(Routes.SETTINGS) {
            val vm: SettingsViewModel = viewModel(factory = AppViewModels.Factory)
            val state by vm.state.collectAsStateWithLifecycle()
            SettingsScreen(
                state = state,
                onBack = navController::popBackStack,
                onThemeMode = vm::setThemeMode,
                // Read once: picking another language recreates the activity with the new value.
                language = remember { AppLanguage.current() },
                onLanguage = AppLanguage::apply,
                onDynamicColor = vm::setDynamicColor,
                onOnlineLyrics = vm::setOnlineLyrics,
                onMinDuration = vm::setMinDuration,
                onRescan = vm::rescan,
                bottomPadding = bottomPadding,
            )
        }
    }
}

@Composable
private fun PermissionScreen(onRequest: () -> Unit, onOpenSettings: () -> Unit) {
    Scaffold(containerColor = Dz.colors.bg) { innerPadding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
        ) {
            DzMark(size = 48.dp)
            DzTitle(text = stringResource(R.string.permission_title), style = DzType.h1)
            Text(
                text = stringResource(R.string.permission_body),
                style = DzType.body,
                color = Dz.colors.inkMuted,
            )
            DzButton(text = stringResource(R.string.permission_grant), arrow = true, onClick = onRequest)
            DzButton(
                text = stringResource(R.string.permission_settings),
                variant = DzButtonVariant.GHOST,
                onClick = onOpenSettings,
            )
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

private fun Context.toast(message: Int) = toast(getString(message))

private fun Context.toast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

/** What the terminal needs to read, captured together so it always sees one consistent moment. */
private data class ShellSnapshot(
    val player: PlayerUiState,
    val library: LibraryIndex,
    val favoriteIds: Set<Long>,
    val mostPlayedIds: List<Long>,
    val lyrics: LyricsUiState,
)
