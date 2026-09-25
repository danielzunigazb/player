package com.danielzuniga.player.ui

import android.net.Uri

object Routes {
    const val ARG_ID = "id"
    const val ARG_KIND = "kind"
    const val ARG_NAME = "name"

    const val HOME = "home"
    const val ALBUM = "album/{$ARG_ID}"
    const val ARTIST = "artist/{$ARG_NAME}"
    const val PLAYLIST = "playlist/{$ARG_KIND}/{$ARG_ID}"
    const val EQUALIZER = "equalizer"
    const val SETTINGS = "settings"

    private const val USER_PLAYLIST = "USER"

    fun album(id: Long) = "album/$id"
    fun artist(name: String) = "artist/${Uri.encode(name)}"
    fun playlist(id: Long) = "playlist/$USER_PLAYLIST/$id"
    fun smartPlaylist(smart: SmartPlaylist) = "playlist/${smart.name}/0"
}
