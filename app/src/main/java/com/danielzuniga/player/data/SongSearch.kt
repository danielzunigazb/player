package com.danielzuniga.player.data

import java.text.Normalizer

/** Case- and accent-insensitive match on title, artist or album ("cancion" finds "Canción"). */
fun filterSongs(songs: List<Song>, query: String): List<Song> {
    val needle = query.trim().normalizedForSearch()
    if (needle.isEmpty()) return songs
    return songs.filter { song ->
        song.title.normalizedForSearch().contains(needle) ||
            song.artist.normalizedForSearch().contains(needle) ||
            song.album.normalizedForSearch().contains(needle)
    }
}

private val DIACRITICS = "\\p{Mn}+".toRegex()

private fun String.normalizedForSearch(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD).replace(DIACRITICS, "").lowercase()
