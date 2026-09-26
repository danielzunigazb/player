package com.danielzuniga.player.data.update

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** A published version of the app. */
data class Release(
    val version: String,
    /** Direct link to the release's APK. */
    val apkUrl: String,
    /** The release page, with the notes. */
    val pageUrl: String,
)

/**
 * Asks GitHub which release of Player is the latest. Only the public "latest release" endpoint
 * is called; nothing about the phone or the library is sent. [fetch] is injectable for tests.
 */
class UpdateChecker(
    private val userAgent: String,
    private val url: String = "https://api.github.com/repos/danielzunigazb/player/releases/latest",
    private val fetch: (url: String, userAgent: String) -> String? = ::httpGet,
) {

    /** The latest release, or null when GitHub can't be reached or the answer isn't usable. Blocking. */
    fun latest(): Release? = fetch(url, userAgent)?.let(::parse)

    companion object {
        internal fun parse(body: String): Release? = runCatching {
            val json = JSONObject(body)
            if (json.optBoolean("draft") || json.optBoolean("prerelease")) return null
            val version = json.getString("tag_name").removePrefix("v")
            val assets = json.optJSONArray("assets")
            val apk = (0 until (assets?.length() ?: 0))
                .map { assets!!.getJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk") }
                ?.optString("browser_download_url")
                ?.takeIf { it.startsWith("https://") }
                ?: return null
            Release(version, apk, json.optString("html_url"))
        }.getOrNull()

        /** Whether [candidate] ("1.6.0") comes after [current] ("1.5.0"), comparing numbers, not text. */
        fun isNewer(candidate: String, current: String): Boolean {
            val a = numbers(candidate) ?: return false
            val b = numbers(current) ?: return false
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }

        private fun numbers(version: String): List<Int>? =
            version.substringBefore('-').split('.').map { it.toIntOrNull() ?: return null }

        private fun httpGet(url: String, userAgent: String): String? = try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            conn.setRequestProperty("User-Agent", userAgent)
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            try {
                if (conn.responseCode == 200) conn.inputStream.bufferedReader().use { it.readText() } else null
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            null
        }
    }
}
