package com.danielzuniga.player.ui.settings

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.danielzuniga.player.BuildConfig
import com.danielzuniga.player.R
import com.danielzuniga.player.ui.components.Eyebrow
import com.danielzuniga.player.ui.components.Hairline
import com.danielzuniga.player.data.ThemeMode
import com.danielzuniga.player.ui.SettingsUiState
import com.danielzuniga.player.ui.components.BackTopBar
import com.danielzuniga.player.ui.theme.DzIcons

private val MIN_DURATION_OPTIONS = listOf(0, 10, 30, 60)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    language: AppLanguage,
    onLanguage: (AppLanguage) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    onOnlineLyrics: (Boolean) -> Unit,
    onMinDuration: (Int) -> Unit,
    onRescan: () -> Unit,
    bottomPadding: PaddingValues,
) {
    Scaffold(
        topBar = { BackTopBar(stringResource(R.string.settings), onBack) },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = bottomPadding.calculateBottomPadding() + 16.dp,
            ),
        ) {
            item { Section(stringResource(R.string.theme)) }
            ThemeMode.entries.forEach { mode ->
                item { RadioRow(stringResource(mode.label()), state.themeMode == mode) { onThemeMode(mode) } }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                item {
                    SwitchRow(
                        title = stringResource(R.string.dynamic_color),
                        summary = stringResource(R.string.dynamic_color_summary),
                        checked = state.dynamicColor,
                        onChange = onDynamicColor,
                    )
                }
            }

            item { Section(stringResource(R.string.language)) }
            AppLanguage.entries.forEach { option ->
                item { RadioRow(stringResource(option.label), language == option) { onLanguage(option) } }
            }

            item { Section(stringResource(R.string.library)) }
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(stringResource(R.string.min_duration), style = MaterialTheme.typography.bodyLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        MIN_DURATION_OPTIONS.forEach { seconds ->
                            FilterChip(
                                selected = state.minDurationSec == seconds,
                                onClick = { onMinDuration(seconds) },
                                label = {
                                    Text(
                                        if (seconds == 0) stringResource(R.string.min_duration_none)
                                        else stringResource(R.string.seconds, seconds)
                                    )
                                },
                            )
                        }
                    }
                }
            }
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onRescan)
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                ) {
                    Icon(DzIcons.Refresh, contentDescription = null)
                    Text(
                        stringResource(R.string.rescan),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 16.dp),
                    )
                }
            }

            item { Section(stringResource(R.string.lyrics)) }
            item {
                SwitchRow(
                    title = stringResource(R.string.online_lyrics),
                    summary = stringResource(R.string.online_lyrics_summary),
                    checked = state.onlineLyrics,
                    onChange = onOnlineLyrics,
                )
            }

            item { Section(stringResource(R.string.about)) }
            item {
                Text(
                    text = stringResource(R.string.version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }
}

private fun ThemeMode.label(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
    ThemeMode.BLACK -> R.string.theme_black
}

@Composable
private fun Section(text: String) {
    Column {
        Hairline(Modifier.padding(top = 16.dp))
        Eyebrow(text = text, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp))
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = 8.dp),
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SwitchRow(title: String, summary: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
