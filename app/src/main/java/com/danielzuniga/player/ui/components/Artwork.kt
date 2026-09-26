package com.danielzuniga.player.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.danielzuniga.player.ui.theme.Dz
import com.danielzuniga.player.ui.theme.DzIcons

/**
 * Album art as a hard-edged block (radius-none); the note placeholder stays visible when the
 * song has no cover.
 */
@Composable
fun Artwork(uri: Uri?, modifier: Modifier = Modifier, cornerRadius: Dp = 0.dp) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(Dz.colors.surface),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = DzIcons.Note,
            contentDescription = null,
            tint = Dz.colors.line,
            modifier = Modifier.fillMaxSize(0.4f),
        )
        if (uri != null) {
            AsyncImage(
                model = uri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}
