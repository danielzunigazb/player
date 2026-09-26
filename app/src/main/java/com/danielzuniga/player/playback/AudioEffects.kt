package com.danielzuniga.player.playback

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class EqBand(val index: Int, val centerHz: Int, val level: Int)

data class EqualizerState(
    val available: Boolean = false,
    val enabled: Boolean = false,
    val bands: List<EqBand> = emptyList(),
    val minLevel: Int = -1500,
    val maxLevel: Int = 1500,
    val presets: List<String> = emptyList(),
    val presetIndex: Int = CUSTOM_PRESET,
    val bassSupported: Boolean = false,
    val bassStrength: Int = 0,
)

const val CUSTOM_PRESET = -1
const val MAX_BASS_STRENGTH = 1000

/**
 * Equalizer and bass boost bound to the player's audio session. Settings persist and are
 * re-applied whenever the session changes. Device audio-effect support varies widely, so
 * every framework call is guarded.
 */
class AudioEffects(context: Context) {

    private val prefs = context.getSharedPreferences("audio_effects", Context.MODE_PRIVATE)
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null

    private val _state = MutableStateFlow(EqualizerState(enabled = prefs.getBoolean(KEY_ENABLED, false)))
    val state: StateFlow<EqualizerState> = _state.asStateFlow()

    fun attach(audioSessionId: Int) {
        release()
        if (audioSessionId <= 0) return
        equalizer = guard { Equalizer(0, audioSessionId) }
        bassBoost = guard { BassBoost(0, audioSessionId) }
        restoreSettings()
        publish()
    }

    fun setEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_ENABLED, enabled) }
        applyEnabled(enabled)
        publish()
    }

    fun setBandLevel(band: Int, level: Int) {
        val eq = equalizer ?: return
        guard { eq.setBandLevel(band.toShort(), level.toShort()) }
        prefs.edit { putInt(KEY_PRESET, CUSTOM_PRESET) }
        saveLevels()
        publish()
    }

    fun usePreset(index: Int) {
        val eq = equalizer ?: return
        guard { eq.usePreset(index.toShort()) }
        prefs.edit { putInt(KEY_PRESET, index) }
        saveLevels()
        publish()
    }

    fun setBassStrength(strength: Int) {
        val value = strength.coerceIn(0, MAX_BASS_STRENGTH)
        prefs.edit { putInt(KEY_BASS, value) }
        bassBoost?.let { bb ->
            guard { bb.setStrength(value.toShort()) }
            guard { bb.enabled = _state.value.enabled && value > 0 }
        }
        publish()
    }

    fun release() {
        guard { equalizer?.release() }
        guard { bassBoost?.release() }
        equalizer = null
        bassBoost = null
    }

    private fun restoreSettings() {
        val eq = equalizer
        if (eq != null) {
            val preset = prefs.getInt(KEY_PRESET, CUSTOM_PRESET)
            val levels = prefs.getString(KEY_LEVELS, null)
                ?.split(',')
                ?.mapNotNull { it.toShortOrNull() }
                .orEmpty()
            val bandCount = guard { eq.numberOfBands.toInt() } ?: 0
            when {
                preset >= 0 -> guard { eq.usePreset(preset.toShort()) }
                levels.size == bandCount -> levels.forEachIndexed { band, level ->
                    guard { eq.setBandLevel(band.toShort(), level) }
                }
            }
        }
        bassBoost?.let { bb -> guard { bb.setStrength(prefs.getInt(KEY_BASS, 0).toShort()) } }
        applyEnabled(prefs.getBoolean(KEY_ENABLED, false))
    }

    private fun applyEnabled(enabled: Boolean) {
        equalizer?.let { guard { it.enabled = enabled } }
        bassBoost?.let { guard { it.enabled = enabled && prefs.getInt(KEY_BASS, 0) > 0 } }
    }

    private fun saveLevels() {
        val eq = equalizer ?: return
        val levels = guard {
            (0 until eq.numberOfBands).map { eq.getBandLevel(it.toShort()) }
        } ?: return
        prefs.edit { putString(KEY_LEVELS, levels.joinToString(",")) }
    }

    private fun publish() {
        val enabled = prefs.getBoolean(KEY_ENABLED, false)
        val eq = equalizer
        val bands = eq?.let {
            guard {
                (0 until it.numberOfBands).map { band ->
                    EqBand(
                        index = band,
                        centerHz = it.getCenterFreq(band.toShort()) / 1000,
                        level = it.getBandLevel(band.toShort()).toInt(),
                    )
                }
            }
        }.orEmpty()
        val range = eq?.let { guard { it.bandLevelRange } }
        val presets = eq?.let {
            guard { (0 until it.numberOfPresets).map { p -> it.getPresetName(p.toShort()) } }
        }.orEmpty()

        _state.value = EqualizerState(
            available = eq != null && bands.isNotEmpty(),
            enabled = enabled,
            bands = bands,
            minLevel = range?.getOrNull(0)?.toInt() ?: -1500,
            maxLevel = range?.getOrNull(1)?.toInt() ?: 1500,
            presets = presets,
            presetIndex = prefs.getInt(KEY_PRESET, CUSTOM_PRESET),
            bassSupported = bassBoost?.let { guard { it.strengthSupported } } == true,
            bassStrength = prefs.getInt(KEY_BASS, 0),
        )
    }

    private inline fun <T> guard(block: () -> T): T? =
        try {
            block()
        } catch (_: RuntimeException) {
            null
        }

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_PRESET = "preset"
        const val KEY_LEVELS = "levels"
        const val KEY_BASS = "bass"
    }
}
