package com.danielzuniga.player.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK, BLACK }

/** Small user preferences, exposed as flows so the UI reacts to changes immediately. */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _songSort = MutableStateFlow(enumPref(KEY_SORT, SongSort.TITLE))
    val songSort: StateFlow<SongSort> = _songSort.asStateFlow()

    private val _themeMode = MutableStateFlow(enumPref(KEY_THEME, ThemeMode.SYSTEM))
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _dynamicColor = MutableStateFlow(prefs.getBoolean(KEY_DYNAMIC_COLOR, false))
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    private val _minDurationSec = MutableStateFlow(prefs.getInt(KEY_MIN_DURATION, 10))
    val minDurationSec: StateFlow<Int> = _minDurationSec.asStateFlow()

    fun setSongSort(sort: SongSort) {
        prefs.edit { putString(KEY_SORT, sort.name) }
        _songSort.value = sort
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit { putString(KEY_THEME, mode.name) }
        _themeMode.value = mode
    }

    fun setDynamicColor(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_DYNAMIC_COLOR, enabled) }
        _dynamicColor.value = enabled
    }

    fun setMinDurationSec(seconds: Int) {
        prefs.edit { putInt(KEY_MIN_DURATION, seconds) }
        _minDurationSec.value = seconds
    }

    private inline fun <reified T : Enum<T>> enumPref(key: String, default: T): T =
        prefs.getString(key, null)
            ?.let { name -> enumValues<T>().firstOrNull { it.name == name } }
            ?: default

    private companion object {
        const val KEY_SORT = "song_sort"
        const val KEY_THEME = "theme_mode"
        const val KEY_DYNAMIC_COLOR = "dynamic_color"
        const val KEY_MIN_DURATION = "min_duration_sec"
    }
}
