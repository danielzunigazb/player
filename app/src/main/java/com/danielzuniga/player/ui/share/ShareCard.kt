package com.danielzuniga.player.ui.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.res.ResourcesCompat
import com.danielzuniga.player.R

/**
 * What goes on a share card. With [lines] it's a lyrics card (the lines big, the song small at
 * the bottom); without, a song card (the cover big).
 */
data class ShareCardContent(
    val title: String,
    val artist: String,
    val album: String,
    val artwork: Bitmap?,
    val lines: List<String> = emptyList(),
)

/**
 * Draws a 9:16 story image in the Daniel Zúñiga design system: obsidian page, the cover as a
 * hard-edged block, the title in JetBrains Mono, the artist as the Instrument Serif whisper in
 * gold, and the ñ mark. Plain Canvas drawing, so it runs off the main thread.
 */
object ShareCard {
    const val WIDTH = 1080
    const val HEIGHT = 1920

    /** Obsidiana tokens (see ui/theme/Theme.kt). */
    private const val BG = 0xFF0E0C0A.toInt()
    private const val SURFACE = 0xFF181512.toInt()
    private const val LINE = 0xFF2F2923.toInt()
    private const val INK = 0xFFEFE6D6.toInt()
    private const val INK_MUTED = 0xFFA2978A.toInt()
    private const val GOLD = 0xFFD6A23E.toInt()

    private const val MARGIN = 96f
    const val FOOTER = "player.danzuniga.xyz"

    fun render(context: Context, content: ShareCardContent): Bitmap {
        val fonts = Fonts(context)
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(BG)
        drawBrand(canvas, fonts)
        if (content.lines.isEmpty()) drawSongCard(canvas, fonts, content) else drawLyricsCard(canvas, fonts, content)
        drawFooter(canvas, fonts)
        return bitmap
    }

    // ---------------------------------------------------------------- layouts

    private fun drawSongCard(canvas: Canvas, fonts: Fonts, content: ShareCardContent) {
        val size = WIDTH - 2 * MARGIN
        val art = RectF(MARGIN, 280f, MARGIN + size, 280f + size)
        drawArtwork(canvas, fonts, content.artwork, art)

        var y = art.bottom + 80f
        y += drawText(canvas, content.title, fonts.monoBold(72f, INK), MARGIN, y, maxLines = 2)
        y += 12f
        y += drawText(canvas, content.artist, fonts.serif(84f, GOLD), MARGIN, y, maxLines = 1)
        y += 16f
        drawText(canvas, content.album, fonts.mono(34f, INK_MUTED), MARGIN, y, maxLines = 1)
    }

    private fun drawLyricsCard(canvas: Canvas, fonts: Fonts, content: ShareCardContent) {
        // The lines, centered between the brand and the song block, a gap between lyric lines
        // so a wrapped line doesn't read as two.
        val total = content.lines.sumOf { it.length }
        val paint = fonts.monoBold(if (total > 140) 58f else 72f, INK)
        val layouts = content.lines.map { layout(it, paint, maxLines = 4) }
        val gap = paint.textSize * 1.0f
        val height = layouts.sumOf { it.height } + gap * (layouts.size - 1)
        val areaTop = 360f
        val areaBottom = 1440f
        val top = (areaTop + (areaBottom - areaTop - height) / 2f).coerceAtLeast(areaTop)
        // The whisper: an open quote in the serif, in gold.
        drawText(canvas, "“", fonts.serif(220f, GOLD), MARGIN - 8f, top - 190f, maxLines = 1)
        var lineTop = top
        for (layout in layouts) {
            canvas.save()
            canvas.translate(MARGIN, lineTop)
            layout.draw(canvas)
            canvas.restore()
            lineTop += layout.height + gap
        }

        // The song, small: cover block and title/artist beside it.
        val art = RectF(MARGIN, 1500f, MARGIN + 200f, 1700f)
        drawArtwork(canvas, fonts, content.artwork, art)
        val x = art.right + 40f
        val width = (WIDTH - MARGIN - x).toInt()
        var y = art.top + 24f
        y += drawText(canvas, content.title, fonts.monoBold(44f, INK), x, y, maxLines = 2, width = width)
        y += 8f
        drawText(canvas, content.artist, fonts.serif(54f, GOLD), x, y, maxLines = 1, width = width)
    }

    // ---------------------------------------------------------------- pieces

    private fun drawBrand(canvas: Canvas, fonts: Fonts) {
        // The ñ mark cut out of a gold block, then the wordmark, as in the app's header.
        val block = RectF(MARGIN, 120f, MARGIN + 72f, 192f)
        canvas.drawRect(block, Paint().apply { color = GOLD })
        val mark = fonts.monoBold(60f, BG).apply { textAlign = Paint.Align.CENTER }
        canvas.drawText("ñ", block.centerX(), block.bottom - 16f, mark)
        val word = fonts.mono(32f, INK).apply { letterSpacing = 0.14f }
        canvas.drawText("PLAYER", block.right + 28f, block.centerY() + 11f, word)
    }

    private fun drawFooter(canvas: Canvas, fonts: Fonts) {
        val paint = fonts.mono(30f, INK_MUTED).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText(FOOTER, WIDTH - MARGIN, HEIGHT - 96f, paint)
        canvas.drawRect(MARGIN, HEIGHT - 150f, WIDTH - MARGIN, HEIGHT - 148f, Paint().apply { color = LINE })
    }

    private fun drawArtwork(canvas: Canvas, fonts: Fonts, artwork: Bitmap?, rect: RectF) {
        if (artwork != null) {
            canvas.drawBitmap(artwork, centerCrop(artwork), rect, Paint(Paint.FILTER_BITMAP_FLAG))
        } else {
            canvas.drawRect(rect, Paint().apply { color = SURFACE })
            val mark = fonts.monoBold(rect.height() * 0.5f, GOLD).apply { textAlign = Paint.Align.CENTER }
            canvas.drawText("ñ", rect.centerX(), rect.centerY() + rect.height() * 0.17f, mark)
        }
        canvas.drawRect(rect, Paint().apply { color = LINE; style = Paint.Style.STROKE; strokeWidth = 2f })
    }

    /** The largest centered square of [bitmap], so any cover fills the block without stretching. */
    private fun centerCrop(bitmap: Bitmap): Rect {
        val side = minOf(bitmap.width, bitmap.height)
        val left = (bitmap.width - side) / 2
        val top = (bitmap.height - side) / 2
        return Rect(left, top, left + side, top + side)
    }

    /** Draws wrapped text with its top at [y]; returns the height used. */
    private fun drawText(
        canvas: Canvas,
        text: String,
        paint: TextPaint,
        x: Float,
        y: Float,
        maxLines: Int,
        width: Int = (WIDTH - 2 * MARGIN).toInt(),
    ): Float {
        if (text.isBlank()) return 0f
        val layout = layout(text, paint, maxLines, width)
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
        return layout.height.toFloat()
    }

    private fun layout(text: String, paint: TextPaint, maxLines: Int, width: Int = (WIDTH - 2 * MARGIN).toInt()) =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.15f)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()

    private class Fonts(context: Context) {
        private val regular = ResourcesCompat.getFont(context, R.font.jetbrains_mono_regular) ?: Typeface.MONOSPACE
        private val bold = ResourcesCompat.getFont(context, R.font.jetbrains_mono_semibold) ?: Typeface.MONOSPACE
        private val italic = ResourcesCompat.getFont(context, R.font.instrument_serif_italic) ?: Typeface.SERIF

        fun mono(size: Float, color: Int) = paint(regular, size, color)
        fun monoBold(size: Float, color: Int) = paint(bold, size, color)
        fun serif(size: Float, color: Int) = paint(italic, size, color)

        private fun paint(typeface: Typeface, size: Float, color: Int) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = size
            this.color = color
        }
    }
}
