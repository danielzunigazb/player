package com.danielzuniga.player.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.danielzuniga.player.R
import com.danielzuniga.player.data.Song
import com.danielzuniga.player.ui.theme.Dz
import com.danielzuniga.player.ui.theme.DzIcons
import com.danielzuniga.player.ui.theme.DzType

/** One gold primary ("play all") and a secondary shuffle, per the one-primary-per-view rule. */
@Composable
fun PlayShuffleButtons(songs: List<Song>, modifier: Modifier = Modifier) {
    val actions = LocalSongActions.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier) {
        DzButton(
            text = stringResource(R.string.play_all),
            icon = DzIcons.Play,
            onClick = { actions.play(songs, 0) },
            enabled = songs.isNotEmpty(),
        )
        DzButton(
            text = stringResource(R.string.shuffle_all),
            icon = DzIcons.Shuffle,
            variant = DzButtonVariant.SECONDARY,
            onClick = { actions.shuffle(songs) },
            enabled = songs.isNotEmpty(),
        )
    }
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
    ) {
        Text(
            text = text,
            style = DzType.small,
            color = Dz.colors.inkMuted,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackTopBar(
    title: String,
    onBack: () -> Unit,
    actions: @Composable () -> Unit = {},
) {
    Column {
        TopAppBar(
            title = { Text(title, style = DzType.small.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = {
                DzIconButton(
                    icon = DzIcons.Back,
                    contentDescription = stringResource(R.string.back),
                    onClick = onBack,
                    bordered = false,
                    modifier = Modifier.padding(start = 8.dp),
                )
            },
            actions = { actions() },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Dz.colors.bg),
        )
        Hairline()
    }
}
