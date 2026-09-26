package com.danielzuniga.player.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.danielzuniga.player.R
import com.danielzuniga.player.data.lyrics.Lyrics
import com.danielzuniga.player.data.lyrics.LyricsSource
import com.danielzuniga.player.ui.components.Eyebrow
import com.danielzuniga.player.ui.theme.Dz
import com.danielzuniga.player.ui.LyricsUiState

@Composable
fun LyricsView(
    state: LyricsUiState,
    positionMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            LyricsBody(state, positionMs, onSeek)
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
private fun LyricsBody(state: LyricsUiState, positionMs: Long, onSeek: (Long) -> Unit) {
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
        is Lyrics.Synced -> SyncedLyrics(lyrics, positionMs, onSeek)
    }
}

@Composable
private fun SyncedLyrics(lyrics: Lyrics.Synced, positionMs: Long, onSeek: (Long) -> Unit) {
    val current = lyrics.indexAt(positionMs)
    val listState = rememberLazyListState()
    LaunchedEffect(current) {
        // Keep the active line about a third of the way down.
        val target = (current - 2).coerceAtLeast(0)
        listState.animateScrollToItem(target)
    }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(lyrics.lines) { index, line ->
            val active = index == current
            Text(
                text = line.text.ifEmpty { "♪" },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSeek(line.timeMs) }
                    .padding(horizontal = 4.dp),
            )
        }
    }
}
