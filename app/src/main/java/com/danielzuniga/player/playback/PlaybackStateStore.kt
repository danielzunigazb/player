package com.danielzuniga.player.playback

import android.content.Context
import androidx.core.content.edit

data class SavedQueue(
    val songIds: List<Long>,
    val index: Int,
    val positionMs: Long,
    val shuffle: Boolean,
    val repeatMode: Int,
)

/** Remembers the queue across app restarts so playback can pick up where it left off. */
class PlaybackStateStore(context: Context) {

    private val prefs = context.getSharedPreferences("playback_state", Context.MODE_PRIVATE)

    fun save(queue: SavedQueue) {
        prefs.edit {
            putString(KEY_IDS, queue.songIds.joinToString(","))
            putInt(KEY_INDEX, queue.index)
            putLong(KEY_POSITION, queue.positionMs)
            putBoolean(KEY_SHUFFLE, queue.shuffle)
            putInt(KEY_REPEAT, queue.repeatMode)
        }
    }

    fun load(): SavedQueue? {
        val ids = prefs.getString(KEY_IDS, null)
            ?.split(',')
            ?.mapNotNull { it.toLongOrNull() }
            .orEmpty()
        if (ids.isEmpty()) return null
        return SavedQueue(
            songIds = ids,
            index = prefs.getInt(KEY_INDEX, 0),
            positionMs = prefs.getLong(KEY_POSITION, 0L),
            shuffle = prefs.getBoolean(KEY_SHUFFLE, false),
            repeatMode = prefs.getInt(KEY_REPEAT, 0),
        )
    }

    private companion object {
        const val KEY_IDS = "ids"
        const val KEY_INDEX = "index"
        const val KEY_POSITION = "position"
        const val KEY_SHUFFLE = "shuffle"
        const val KEY_REPEAT = "repeat"
    }
}
