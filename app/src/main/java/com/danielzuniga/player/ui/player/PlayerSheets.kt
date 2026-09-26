package com.danielzuniga.player.ui.player

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.danielzuniga.player.R
import com.danielzuniga.player.ui.components.DzButton
import com.danielzuniga.player.ui.components.DzButtonVariant
import com.danielzuniga.player.playback.PlayerUiState
import com.danielzuniga.player.playback.QueueItem
import com.danielzuniga.player.playback.QueueState
import com.danielzuniga.player.playback.SessionCommands
import com.danielzuniga.player.ui.components.Artwork
import com.danielzuniga.player.ui.formatDuration
import kotlinx.coroutines.delay
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.math.BigDecimal
import com.danielzuniga.player.ui.theme.DzIcons

fun formatSpeed(speed: Float): String =
    BigDecimal(speed.toString()).stripTrailingZeros().toPlainString() + "x"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
    queue: QueueState,
    onDismiss: () -> Unit,
    onSkipTo: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Local order for smooth dragging; the player is updated once the item is dropped.
    var items by remember { mutableStateOf(queue.items) }
    var dragStartIndex by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(queue.items) { if (dragStartIndex == null) items = queue.items }

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = queue.items.indexOfFirst { it.index == queue.currentIndex }.coerceAtLeast(0),
    )
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromPos = items.indexOfFirst { it.index == from.key }
        val toPos = items.indexOfFirst { it.index == to.key }
        if (fromPos >= 0 && toPos >= 0) {
            items = items.toMutableList().apply { add(toPos, removeAt(fromPos)) }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxHeight(0.85f)) {
            Text(
                text = stringResource(R.string.queue),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            if (queue.shuffled) {
                Text(
                    text = stringResource(R.string.queue_shuffled_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
            }
            LazyColumn(state = listState, modifier = Modifier.padding(top = 8.dp)) {
                items(items, key = { it.index }) { item ->
                    ReorderableItem(reorderState, key = item.index, enabled = !queue.shuffled) { isDragging ->
                        val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "drag")
                        Surface(shadowElevation = elevation) {
                            QueueRow(
                                item = item,
                                isCurrent = item.index == queue.currentIndex,
                                onClick = { onSkipTo(item.index) },
                                onRemove = { onRemove(item.index) },
                                dragHandle = {
                                    if (!queue.shuffled) {
                                        IconButton(
                                            onClick = {},
                                            modifier = Modifier.draggableHandle(
                                                onDragStarted = { dragStartIndex = item.index },
                                                onDragStopped = {
                                                    val from = dragStartIndex
                                                    val to = items.indexOfFirst { it.index == from }
                                                    dragStartIndex = null
                                                    if (from != null && to >= 0 && from != to) onMove(from, to)
                                                },
                                            ),
                                        ) {
                                            Icon(DzIcons.DragHandle, contentDescription = stringResource(R.string.reorder))
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueRow(
    item: QueueItem,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    dragHandle: @Composable () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isCurrent) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Artwork(uri = item.artworkUri, modifier = Modifier.size(44.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrent) FontWeight.SemiBold else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!isCurrent) {
            IconButton(onClick = onRemove) {
                Icon(DzIcons.Close, contentDescription = stringResource(R.string.remove))
            }
        }
        dragHandle()
    }
}

private val SLEEP_OPTIONS = listOf(5, 10, 15, 30, 45, 60, 90)
private val SPEED_OPTIONS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SleepTimerSheet(state: PlayerUiState, onSet: (Int) -> Unit, onDismiss: () -> Unit) {
    val now by produceState(System.currentTimeMillis(), state.sleepAtMs) {
        while (state.sleepAtMs > 0) {
            value = System.currentTimeMillis()
            delay(1_000)
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text(stringResource(R.string.sleep_timer), style = MaterialTheme.typography.titleLarge)
            val status = when {
                state.sleepAtEndOfTrack -> stringResource(R.string.sleep_remaining_end_of_track)
                state.sleepAtMs > 0 -> stringResource(R.string.sleep_remaining, formatDuration(state.sleepAtMs - now))
                else -> null
            }
            if (status != null) {
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 16.dp),
            ) {
                SLEEP_OPTIONS.forEach { minutes ->
                    FilterChip(
                        selected = false,
                        onClick = { onSet(minutes); onDismiss() },
                        label = { Text(stringResource(R.string.sleep_minutes, minutes)) },
                    )
                }
                FilterChip(
                    selected = state.sleepAtEndOfTrack,
                    onClick = { onSet(SessionCommands.SLEEP_END_OF_TRACK); onDismiss() },
                    label = { Text(stringResource(R.string.sleep_end_of_track)) },
                )
            }
            if (status != null) {
                DzButton(
                    text = stringResource(R.string.sleep_off),
                    variant = DzButtonVariant.DANGER,
                    onClick = { onSet(SessionCommands.SLEEP_OFF); onDismiss() },
                    modifier = Modifier.padding(top = 16.dp).fillMaxWidth(),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SpeedSheet(current: Float, onSet: (Float) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text(stringResource(R.string.speed), style = MaterialTheme.typography.titleLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 16.dp),
            ) {
                SPEED_OPTIONS.forEach { speed ->
                    FilterChip(
                        selected = speed == current,
                        onClick = { onSet(speed); onDismiss() },
                        label = { Text(formatSpeed(speed)) },
                    )
                }
            }
        }
    }
}
