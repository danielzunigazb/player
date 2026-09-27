package com.danielzuniga.player.remote

import org.json.JSONObject

/** Numbers what one sender sends: its id, a rising seq and the time (docs/monitor.md). */
class Outbox(private val from: String) {
    private var seq = 0L

    fun stamp(message: JSONObject, now: Long = System.currentTimeMillis()): JSONObject =
        message.put("from", from).put("seq", ++seq).put("ts", now)
}

/** Lets through only messages newer than the last one from the same sender, and not stale. */
class Inbox {
    private val last = HashMap<String, Long>()

    fun accept(message: JSONObject, now: Long = System.currentTimeMillis()): Boolean {
        val from = message.optString("from").ifEmpty { return false }
        val seq = message.optLong("seq", -1)
        val ts = message.optLong("ts", -1)
        if (seq <= 0 || ts <= 0) return false
        if (kotlin.math.abs(now - ts) > MAX_SKEW_MS) return false
        if (seq <= (last[from] ?: 0L)) return false
        last[from] = seq
        return true
    }

    companion object {
        const val MAX_SKEW_MS = 2 * 60 * 1000L
    }
}
