package com.danielzuniga.player.ui.equalizer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.danielzuniga.player.R
import com.danielzuniga.player.playback.CUSTOM_PRESET
import com.danielzuniga.player.playback.EqBand
import com.danielzuniga.player.playback.EqualizerState
import com.danielzuniga.player.playback.MAX_BASS_STRENGTH
import com.danielzuniga.player.ui.components.BackTopBar
import com.danielzuniga.player.ui.components.EmptyState
import kotlin.math.roundToInt

@Composable
fun EqualizerScreen(
    state: EqualizerState,
    onBack: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onPreset: (Int) -> Unit,
    onBandLevel: (band: Int, level: Int) -> Unit,
    onBassStrength: (Int) -> Unit,
    bottomPadding: PaddingValues,
) {
    Scaffold(
        topBar = { BackTopBar(stringResource(R.string.equalizer), onBack) },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        if (!state.available) {
            EmptyState(stringResource(R.string.equalizer_unavailable), Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = bottomPadding.calculateBottomPadding() + 16.dp,
            ),
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        stringResource(R.string.equalizer_enabled),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = state.enabled, onCheckedChange = onEnabledChange)
                }
            }
            item { Section(stringResource(R.string.equalizer_presets)) }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.presetIndex == CUSTOM_PRESET) {
                        item {
                            FilterChip(
                                selected = true,
                                onClick = {},
                                label = { Text(stringResource(R.string.equalizer_custom)) },
                                enabled = state.enabled,
                            )
                        }
                    }
                    itemsIndexed(state.presets) { index, name ->
                        FilterChip(
                            selected = index == state.presetIndex,
                            onClick = { onPreset(index) },
                            label = { Text(name) },
                            enabled = state.enabled,
                        )
                    }
                }
            }
            item { Section(stringResource(R.string.equalizer_bands)) }
            items(state.bands, key = { it.index }) { band ->
                BandSlider(band, state, onBandLevel)
            }
            if (state.bassSupported) {
                item { Section(stringResource(R.string.bass_boost)) }
                item {
                    var strength by remember(state.bassStrength) { mutableFloatStateOf(state.bassStrength.toFloat()) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    ) {
                        Slider(
                            value = strength,
                            onValueChange = { strength = it },
                            onValueChangeFinished = { onBassStrength(strength.roundToInt()) },
                            valueRange = 0f..MAX_BASS_STRENGTH.toFloat(),
                            enabled = state.enabled,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${(strength / MAX_BASS_STRENGTH * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .width(48.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BandSlider(band: EqBand, state: EqualizerState, onBandLevel: (Int, Int) -> Unit) {
    var level by remember(band.level) { mutableFloatStateOf(band.level.toFloat()) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 16.dp),
    ) {
        Text(
            text = if (band.centerHz >= 1000) {
                stringResource(R.string.frequency_khz, band.centerHz / 1000f)
            } else {
                stringResource(R.string.frequency_hz, band.centerHz)
            },
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(64.dp),
        )
        Slider(
            value = level,
            onValueChange = { level = it },
            onValueChangeFinished = { onBandLevel(band.index, level.roundToInt()) },
            valueRange = state.minLevel.toFloat()..state.maxLevel.toFloat(),
            enabled = state.enabled,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.decibels, level / 100f),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .padding(start = 12.dp)
                .width(56.dp),
        )
    }
}

@Composable
private fun Section(text: String) {
    Column {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
        )
    }
}
