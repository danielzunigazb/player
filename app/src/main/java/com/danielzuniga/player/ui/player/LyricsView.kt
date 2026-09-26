package com.danielzuniga.player.ui.player

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.danielzuniga.player.R
import com.danielzuniga.player.data.lyrics.Lyrics
import com.danielzuniga.player.data.lyrics.LyricsSource
import com.danielzuniga.player.ui.LyricsUiState
import com.danielzuniga.player.ui.components.DzButton
import com.danielzuniga.player.ui.components.DzButtonVariant
import com.danielzuniga.player.ui.components.Eyebrow
import com.danielzuniga.player.ui.share.SongSharer
import com.danielzuniga.player.ui.theme.Dz
import com.danielzuniga.player.ui.theme.DzIcons
import com.danielzuniga.player.ui.toast

@Composable
fun LyricsView(
    state: LyricsUiState,
    positionMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onShareLines: (List<String>) -> Unit = {},
) {
    Column(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            LyricsBody(state, positionMs, onSeek, onShareLines)
        }
        if (state.source == LyricsSource.ONLINE) {
            // Credit the free database the lyrics came from.
            Eyebrow(
                text = stringResource(R.string.lyrics_source_online),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun LyricsBody(state: LyricsUiState, positionMs: Long, onSeek: (Long) -> Unit, onShareLines: (List<String>) -> Unit) {
    when (val lyrics = state.lyrics) {
        null -> if (state.loading) {
            CircularProgressIndicator(color = Dz.colors.gold, strokeWidth = 2.dp)
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.lyrics_none),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.lyrics_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        is Lyrics.Plain -> Text(
            text = lyrics.text,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        )
        is Lyrics.Synced -> SyncedLyrics(lyrics, positionMs, onSeek, onShareLines)
    }
}

/**
 * Tap a line to jump to it. Hold one to start picking lines to share (up to
 * [SongSharer.MAX_LINES]); while picking, taps add or remove lines and the list stops following
 * the song.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SyncedLyrics(
    lyrics: Lyrics.Synced,
    positionMs: Long,
    onSeek: (Long) -> Unit,
    onShareLines: (List<String>) -> Unit,
) {
    val current = lyrics.indexAt(positionMs)
    val listState = rememberLazyListState()
    var selected by remember(lyrics) { mutableStateOf(emptySet<Int>()) }
    val context = LocalContext.current
    val limitMessage = stringResource(R.string.share_lines_limit, SongSharer.MAX_LINES)

    fun toggle(index: Int) {
        selected = when {
            index in selected -> selected - index
            selected.size >= SongSharer.MAX_LINES -> selected.also { context.toast(limitMessage) }
            else -> selected + index
        }
    }

    LaunchedEffect(current, selected.isEmpty()) {
        // Keep the active line about a third of the way down, unless lines are being picked.
        if (selected.isNotEmpty()) return@LaunchedEffect
        val target = (current - 2).coerceAtLeast(0)
        listState.animateScrollToItem(target)
    }
    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            itemsIndexed(lyrics.lines) { index, line ->
                val active = index == current
                val picked = index in selected
                val shareable = line.text.isNotBlank()
                Text(
                    text = line.text.ifEmpty { "♪" },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = if (active || picked) FontWeight.Bold else FontWeight.Normal,
                    color = when {
                        picked -> Dz.colors.ink
                        active -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (picked) Dz.colors.surface else Color.Transparent)
                        .combinedClickable(
                            onClick = { if (selected.isEmpty()) onSeek(line.timeMs) else if (shareable) toggle(index) },
                            onLongClick = { if (shareable) toggle(index) },
                        )
                        .padding(horizontal = 4.dp),
                )
            }
        }
        if (selected.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                DzButton(
                    text = pluralStringResource(R.plurals.share_lines, selected.size, selected.size),
                    onClick = {
                        onShareLines(selected.sorted().map { lyrics.lines[it].text })
                        selected = emptySet()
                    },
                    icon = DzIcons.Share,
                )
                DzButton(
                    text = stringResource(R.string.cancel),
                    onClick = { selected = emptySet() },
                    variant = DzButtonVariant.GHOST,
                )
            }
        } else {
            Eyebrow(text = stringResource(R.string.share_lyrics_hint), modifier = Modifier.padding(top = 8.dp))
        }
    }
}
