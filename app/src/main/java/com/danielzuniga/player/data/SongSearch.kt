package com.danielzuniga.player.data

import java.text.Normalizer

/** Case- and accent-insensitive match on title, artist or album ("cancion" finds "Canción"). */
fun filterSongs(songs: List<Song>, query: String): List<Song> {
    val needle = query.normalizedForSearch()
    if (needle.isEmpty()) return songs
    return songs.filter { song ->
        song.title.matches(needle) || song.artist.matches(needle) || song.album.matches(needle)
    }
}

fun filterAlbums(albums: List<Album>, query: String): List<Album> {
    val needle = query.normalizedForSearch()
    if (needle.isEmpty()) return albums
    return albums.filter { it.title.matches(needle) || it.artist.matches(needle) }
}

fun filterArtists(artists: List<Artist>, query: String): List<Artist> {
    val needle = query.normalizedForSearch()
    if (needle.isEmpty()) return artists
    return artists.filter { it.name.matches(needle) }
}

fun filterFolders(folders: List<Folder>, query: String): List<Folder> {
    val needle = query.normalizedForSearch()
    if (needle.isEmpty()) return folders
    return folders.filter { it.name.matches(needle) }
}

private fun String.matches(needle: String): Boolean = normalizedForSearch().contains(needle)

private val DIACRITICS = "\\p{Mn}+".toRegex()

private fun String.normalizedForSearch(): String =
    Normalizer.normalize(trim(), Normalizer.Form.NFD).replace(DIACRITICS, "").lowercase()
