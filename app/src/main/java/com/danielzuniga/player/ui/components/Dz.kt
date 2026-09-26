package com.danielzuniga.player.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.danielzuniga.player.ui.theme.Dz
import com.danielzuniga.player.ui.theme.DzIcons
import com.danielzuniga.player.ui.theme.DzType
import com.danielzuniga.player.ui.theme.SerifFamily

// Compose ports of the DZ design-system components (Button, Tag, Title, Mark) plus the
// small primitives they share. Values come straight from the tokens.

val DzControlShape = RoundedCornerShape(2.dp)

enum class DzButtonVariant { PRIMARY, SECONDARY, GHOST, DANGER }

/** `DZ.Button`: mono semibold, 38dp tall, radius-sm. Labels are lower-case verbs. */
@Composable
fun DzButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: DzButtonVariant = DzButtonVariant.PRIMARY,
    icon: ImageVector? = null,
    arrow: Boolean = false,
    enabled: Boolean = true,
) {
    val c = Dz.colors
    val (container, content, border) = when (variant) {
        DzButtonVariant.PRIMARY -> Triple(c.gold, c.onGold, null)
        DzButtonVariant.SECONDARY -> Triple(Color.Transparent, c.ink, BorderStroke(1.dp, c.inkMuted))
        DzButtonVariant.GHOST -> Triple(Color.Transparent, c.inkMuted, null)
        DzButtonVariant.DANGER -> Triple(Color.Transparent, c.ember, BorderStroke(1.dp, c.ember))
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = DzControlShape,
        color = container,
        contentColor = content,
        border = border,
        modifier = modifier
            .defaultMinSize(minHeight = 38.dp)
            .alpha(if (enabled) 1f else 0.45f),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            modifier = Modifier.padding(PaddingValues(horizontal = 16.dp, vertical = 8.dp)),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(text.lowercase(), style = DzType.small.copy(fontWeight = FontWeight.SemiBold))
            if (arrow) Icon(DzIcons.ArrowRight, contentDescription = null, modifier = Modifier.size(14.dp))
        }
    }
}

/** Square icon-only button; `secondary` gets the ink-muted hairline border. */
@Composable
fun DzIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    bordered: Boolean = true,
    tint: Color = Dz.colors.ink,
) {
    Surface(
        onClick = onClick,
        shape = DzControlShape,
        color = Color.Transparent,
        contentColor = tint,
        border = if (bordered) BorderStroke(1.dp, Dz.colors.line) else null,
        modifier = modifier.size(size),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(18.dp))
        }
    }
}

enum class DzTone { NEUTRAL, VERDIGRIS, GOLD, EMBER }

/** `DZ.Tag`: label style, 1px border, optional status dot (pulsing when live). */
@Composable
fun DzTag(text: String, modifier: Modifier = Modifier, tone: DzTone = DzTone.NEUTRAL, dot: Boolean = false, live: Boolean = false) {
    val c = Dz.colors
    val color = when (tone) {
        DzTone.NEUTRAL -> c.inkMuted
        DzTone.VERDIGRIS -> c.verdigris
        DzTone.GOLD -> c.gold
        DzTone.EMBER -> c.ember
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .border(1.dp, if (tone == DzTone.NEUTRAL) c.line else color, DzControlShape)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        if (dot) StatusDot(color, pulse = live)
        Text(text.uppercase(), style = DzType.label, color = color)
    }
}

/** The only round thing in the system: a 6dp status dot. `pulse` is the "live" heartbeat. */
@Composable
fun StatusDot(color: Color, pulse: Boolean = false, modifier: Modifier = Modifier) {
    val alpha = if (pulse) {
        val a by rememberInfiniteTransition(label = "live").animateFloat(
            initialValue = 1f,
            targetValue = 0.35f,
            animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
            label = "liveAlpha",
        )
        a
    } else {
        1f
    }
    Box(
        modifier
            .size(6.dp)
            .alpha(alpha)
            .background(color, CircleShape),
    )
}

/** Eyebrow line in the label style: section numbering, dates, context. */
@Composable
fun Eyebrow(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Dz.colors.inkMuted,
    textAlign: TextAlign? = null,
) {
    Text(text.uppercase(), style = DzType.label, color = color, textAlign = textAlign, modifier = modifier)
}

/**
 * `DZ.Title`: mono title whose last words can be the serif-italic gold whisper, with the
 * blinking block cursor for the one hero on a screen.
 */
@Composable
fun DzTitle(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    whisper: String? = null,
    whisperOnNewLine: Boolean = false,
    cursor: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
) {
    val c = Dz.colors
    val annotated = remember(text, whisper, whisperOnNewLine, c.gold) {
        buildAnnotatedString {
            append(text)
            if (whisper != null) {
                append(if (whisperOnNewLine) "\n" else " ")
                withStyle(SpanStyle(fontFamily = SerifFamily, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Normal, color = c.gold, letterSpacing = 0.em)) {
                    append(whisper)
                }
            }
        }
    }
    Row(verticalAlignment = Alignment.Bottom, modifier = modifier) {
        Text(
            annotated,
            style = style,
            color = c.ink,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (cursor) BlockCursor(height = with(LocalDensity.current) { style.fontSize.toDp() * 0.82f })
    }
}

/** Gold block that blinks on a hard step, like a terminal caret. */
@Composable
fun BlockCursor(height: Dp, modifier: Modifier = Modifier) {
    val visible by rememberInfiniteTransition(label = "cursor").animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 1100
                1f at 0
                1f at 549
                0f at 550
                0f at 1099
            },
        ),
        label = "blink",
    )
    Box(
        modifier
            .padding(start = 4.dp, bottom = height * 0.18f)
            .size(width = height * 0.6f, height = height)
            .alpha(visible)
            .background(Dz.colors.gold),
    )
}

/** 1px hairline in the `line` token: how the system separates things instead of shadows. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    HorizontalDivider(thickness = 1.dp, color = Dz.colors.line, modifier = modifier)
}

private const val MARK_PATH =
    "M91.5 596.5V211.5H193V285H223.8L193 309.5Q193 260.5 221 232.5Q249 204.5 296.6 204.5Q352.6 204.5 386.5 243.7Q420.5 282.9 420.5 348V596.5H315.5V358.5Q315.5 328.4 299.8 312Q284 295.5 255.3 295.5Q227.3 295.5 211.9 312Q196.5 328.4 196.5 358.5V596.5ZM307.8 152Q285.4 152 271.8 145.7Q258.1 139.4 249 131Q239.9 122.6 231.9 116.3Q223.8 110 213.3 110Q204.2 110 198.2 116.3Q192.3 122.6 192.3 133.1V148.5H132.8V124Q132.8 87.6 154.2 63.8Q175.5 40 209.8 40Q232.2 40 245.8 46.3Q259.5 52.6 268.6 61Q277.7 69.4 285.8 75.7Q293.8 82 304.3 82Q314.1 82 319.7 76.8Q325.3 71.5 325.3 62.4V43.5H384.8V68Q384.8 104.4 363.8 128.2Q342.8 152 307.8 152Z"

/** `DZ.Mark`: the ñ cut out of a gold block, legs bleeding off the bottom edge. */
@Composable
fun DzMark(size: Dp, modifier: Modifier = Modifier, ink: Boolean = false, label: String = "Daniel Zúñiga") {
    val color = if (ink) Dz.colors.ink else Dz.colors.gold
    val glyph = remember { PathParser().parsePathString(MARK_PATH).toPath() }
    Canvas(
        modifier
            .size(size)
            .semantics { contentDescription = label }
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
    ) {
        drawRect(color)
        val scale = this.size.width / 512f
        val scaled = Path().apply {
            addPath(glyph)
            transform(Matrix().apply { scale(scale, scale) })
        }
        drawPath(scaled, Color.Black, blendMode = BlendMode.Clear)
    }
}
