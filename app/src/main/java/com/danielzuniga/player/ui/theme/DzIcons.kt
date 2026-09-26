package com.danielzuniga.player.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The design system's icon set: 24-unit grid, 1.5 stroke, square caps, mitred corners, no
 * fill. The first block is copied verbatim from the DZ bundle; the player-specific ones below
 * were drawn with the same rules. They inherit the tint of the surrounding content colour.
 */
object DzIcons {
    // ---- From the DZ design system
    val ArrowRight = icon("arrow-right", "M4 12h16M14 6l6 6-6 6")
    val ArrowUpRight = icon("arrow-up-right", "M6.5 17.5l11-11M8 6.5h9.5V16")
    val ArrowDown = icon("arrow-down", "M12 4v16M6 14l6 6 6-6")
    val Terminal = icon("terminal", "M3 4h18v16H3zM7 9l3 3-3 3M13 15h4")
    val Spark = icon("spark", "M12 2v5M12 17v5M2 12h5M17 12h5M12 8l4 4-4 4-4-4z")
    val Check = icon("check", "M4 12.5l5 5L20 6.5")
    val Close = icon("close", "M5 5l14 14M19 5L5 19")
    val Menu = icon("menu", "M4 7h16M4 12h16M4 17h10")
    val Moon = icon("moon", "M20 14.5A8.5 8.5 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z")
    val Search = icon("search", "M10.5 4a6.5 6.5 0 1 0 0 13 6.5 6.5 0 0 0 0-13zM15.5 15.5L21 21")
    val Download = icon("download", "M12 3v12M6 10l6 6 6-6M4 20h16")

    // ---- Player set, same rules
    val Play = icon("play", "M7 4.5v15l12-7.5z")
    val Pause = icon("pause", "M8 5v14M16 5v14")
    val Next = icon("next", "M5 5v14l10-7zM19 5v14")
    val Previous = icon("previous", "M19 5v14L9 12zM5 5v14")
    val Shuffle = icon("shuffle", "M3 7h4l10 10h3M3 17h4l3-3M14 10l3-3h3M17.5 4.5L20 7l-2.5 2.5M17.5 14.5L20 17l-2.5 2.5")
    val Repeat = icon("repeat", "M4 11V7h15M16 4l3 3-3 3M20 13v4H5M8 20l-3-3 3-3")
    val RepeatOne = icon("repeat-one", "M4 11V7h15M16 4l3 3-3 3M20 13v4H5M8 20l-3-3 3-3M11.5 10.5l1-.5v4")
    val Heart = icon("heart", "M12 20l-8-8a4.6 4.6 0 0 1 8-5 4.6 4.6 0 0 1 8 5z")
    val HeartFilled = icon("heart-filled", "M12 20l-8-8a4.6 4.6 0 0 1 8-5 4.6 4.6 0 0 1 8 5z", filled = true)
    val Queue = icon("queue", "M4 6h16M4 11h16M4 16h8M15 14v7l6-3.5z")
    val PlayNext = icon("play-next", "M4 6h9M4 11h9M4 16h9M16 7v8l5-4z")
    val AddToQueue = icon("add-to-queue", "M4 6h16M4 11h16M4 16h8M18 14v6M15 17h6")
    val PlaylistAdd = icon("playlist-add", "M3 4h18v16H3zM12 8v8M8 12h8")
    val Lyrics = icon("lyrics", "M4 4h16v12H10l-6 4zM8 8h8M8 12h5")
    val Equalizer = icon("equalizer", "M6 20V11M12 20V4M18 20v-7M4 11h4M10 7h4M16 13h4")
    val More = icon("more", "M12 4.5v1.5M12 11.25v1.5M12 18v1.5")
    val Settings = icon("settings", "M4 7h9M17 7h3M15 5v4M4 17h3M11 17h9M9 15v4")
    val Sort = icon("sort", "M4 6h16M4 12h11M4 18h6M18 14v6M15.5 17.5L18 20l2.5-2.5")
    val Back = icon("back", "M20 12H4M10 6l-6 6 6 6")
    val ChevronDown = icon("chevron-down", "M6 9l6 6 6-6")
    val Folder = icon("folder", "M3 5h7l2 2.5h9V19H3z")
    val Album = icon("album", "M3 3h18v18H3zM12 8.5a3.5 3.5 0 1 0 0 7 3.5 3.5 0 0 0 0-7z")
    val Artist = icon("artist", "M12 4a4 4 0 1 0 0 8 4 4 0 0 0 0-8zM4 21v-2q0-4 4-4h8q4 0 4 4v2")
    val Note = icon("note", "M9 18V5l11-2v13M9 18a3 3 0 1 1-6 0 3 3 0 0 1 6 0zM20 16a3 3 0 1 1-6 0 3 3 0 0 1 6 0z")
    val Library = icon("library", "M4 4v16M8 4v16M12 5l4-1 4 16-4 1z")
    val Add = icon("add", "M12 4v16M4 12h16")
    val Remove = icon("remove", "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM8 12h8")
    val Delete = icon("delete", "M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13")
    val Edit = icon("edit", "M4 20v-4L16 4l4 4L8 20zM13 7l4 4")
    val DragHandle = icon("drag", "M4 9h16M4 15h16")
    val Refresh = icon("refresh", "M20 12a8 8 0 1 1-2.3-5.7M20 4v5h-5")
    val History = icon("history", "M4 12a8 8 0 1 0 2.3-5.7M4 4v5h5M12 8v4l3 2")
    val Trending = icon("trending", "M3 17l6-6 4 4 8-8M15 7h6v6")
    val New = icon("new", "M12 3l2.5 6.5L21 12l-6.5 2.5L12 21l-2.5-6.5L3 12l6.5-2.5z")

    private fun icon(name: String, path: String, filled: Boolean = false): ImageVector =
        ImageVector.Builder(
            name = "dz-$name",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = addPathNodes(path),
            fill = if (filled) SolidColor(Color.Black) else null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Square,
            strokeLineJoin = StrokeJoin.Miter,
        ).build()
}
