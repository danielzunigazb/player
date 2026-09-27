package com.danielzuniga.player.remote

import androidx.media3.common.C
import androidx.media3.common.Player
import com.danielzuniga.player.data.LibraryIndex
import com.danielzuniga.player.data.Song
import com.danielzuniga.player.playback.playbackOrder
import com.danielzuniga.player.playback.toMediaItem
import org.json.JSONArray
import org.json.JSONObject

/**
 * The monitor's messages on the phone's side (docs/monitor.md): the state it shows, and the
 * commands it sends, applied to the same player the notification, widget and Android Auto use.
 */
class RemoteControl(
    private val player: Player,
    private val library: () -> LibraryIndex,
    private val volume: Volume,
) {
    /** The phone's media volume, 0 to 1. */
    interface Volume {
        fun get(): Float
        fun set(value: Float)
    }

    fun state(): JSONObject {
        val item = player.currentMediaItem
        val song = item?.mediaId?.toLongOrNull()?.let { library().song(it) }
        val order = player.currentTimeline.playbackOrder(player.shuffleModeEnabled)
        val current = order.indexOf(player.currentMediaItemIndex).coerceAtLeast(0)
        // A window around the current song: a whole library in the queue wouldn't fit a message.
        val start = (current - QUEUE_BEFORE).coerceAtLeast(0)
        val window = order.subList(start, minOf(order.size, start + QUEUE_WINDOW))
        return JSONObject()
            .put("type", "state")
            .put("song", song?.toJson() ?: item?.let {
                JSONObject()
                    .put("id", it.mediaId)
                    .put("title", it.mediaMetadata.title?.toString().orEmpty())
                    .put("artist", it.mediaMetadata.artist?.toString().orEmpty())
                    .put("album", it.mediaMetadata.albumTitle?.toString().orEmpty())
                    .put("durationMs", player.duration.takeIf { d -> d != C.TIME_UNSET } ?: 0L)
            } ?: JSONObject.NULL)
            .put("playing", player.isPlaying)
            .put("positionMs", player.currentPosition)
            .put("shuffle", player.shuffleModeEnabled)
            .put("repeat", REPEAT_NAMES[player.repeatMode] ?: "off")
            .put("queueStart", start)
            .put("queueTotal", order.size)
            .put("index", current)
            .put("queue", JSONArray().apply {
                for (index in window) {
                    val queued = player.getMediaItemAt(index)
                    put(
                        JSONObject()
                            .put("i", index)
                            .put("id", queued.mediaId)
                            .put("title", queued.mediaMetadata.title?.toString().orEmpty())
                            .put("artist", queued.mediaMetadata.artist?.toString().orEmpty())
                    )
                }
            })
            .put("volume", volume.get().toDouble())
    }

    /** Applies a browser's command. Returns the reply to send back, if the command has one. */
    fun handle(command: JSONObject): JSONObject? {
        if (command.optString("type") != "cmd") return null
        when (command.optString("op")) {
            "play" -> play()
            "pause" -> player.pause()
            "next" -> player.seekToNext()
            "previous" -> player.seekToPrevious()
            "seek" -> player.seekTo(command.optLong("positionMs").coerceAtLeast(0L))
            "shuffle" -> player.shuffleModeEnabled = command.optBoolean("on")
            "repeat" -> REPEAT_MODES[command.optString("mode")]?.let { player.repeatMode = it }
            "volume" -> volume.set(command.optDouble("value", -1.0).toFloat().takeIf { it in 0f..1f } ?: return null)
            "skipTo" -> command.queueIndex("index")?.let {
                player.seekToDefaultPosition(it)
                play()
            }
            "remove" -> command.queueIndex("index")?.let(player::removeMediaItem)
            "move" -> {
                val from = command.queueIndex("from") ?: return null
                val to = command.queueIndex("to") ?: return null
                player.moveMediaItem(from, to)
            }
            "search" -> {
                val query = command.optString("query").take(MAX_QUERY)
                return JSONObject()
                    .put("type", "results")
                    .put("query", query)
                    .put("songs", JSONArray(library().filterSongs(query).take(MAX_RESULTS).map { it.toJson() }))
            }
            "playSongs" -> {
                val songs = songs(command)
                if (songs.isEmpty()) return null
                player.setMediaItems(songs.map { it.toMediaItem() }, command.optInt("index").coerceIn(songs.indices), 0L)
                play()
            }
            "playNext" -> enqueue(songs(command), next = true)
            "addToQueue" -> enqueue(songs(command), next = false)
            "hello" -> Unit
            else -> return null
        }
        return null
    }

    private fun play() {
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
        if (player.playbackState == Player.STATE_ENDED) player.seekToDefaultPosition(player.currentMediaItemIndex)
        player.play()
    }

    private fun enqueue(songs: List<Song>, next: Boolean) {
        if (songs.isEmpty()) return
        val items = songs.map { it.toMediaItem() }
        when {
            player.mediaItemCount == 0 -> {
                player.setMediaItems(items)
                play()
            }
            next -> player.addMediaItems(player.currentMediaItemIndex + 1, items)
            else -> player.addMediaItems(items)
        }
    }

    private fun songs(command: JSONObject): List<Song> {
        val ids = command.optJSONArray("ids") ?: return emptyList()
        val index = library()
        return (0 until minOf(ids.length(), MAX_SONGS)).mapNotNull { index.song(ids.optLong(it, -1)) }
    }

    private fun JSONObject.queueIndex(name: String): Int? =
        optInt(name, -1).takeIf { it in 0 until player.mediaItemCount }

    private fun Song.toJson() = JSONObject()
        .put("id", id.toString())
        .put("title", title)
        .put("artist", artist)
        .put("album", album)
        .put("durationMs", durationMs)

    private companion object {
        const val QUEUE_BEFORE = 20
        const val QUEUE_WINDOW = 150
        const val MAX_RESULTS = 50
        const val MAX_QUERY = 100
        const val MAX_SONGS = 1000

        val REPEAT_MODES = mapOf(
            "off" to Player.REPEAT_MODE_OFF,
            "all" to Player.REPEAT_MODE_ALL,
            "one" to Player.REPEAT_MODE_ONE,
        )
        val REPEAT_NAMES = REPEAT_MODES.entries.associate { (name, mode) -> mode to name }
    }
}
