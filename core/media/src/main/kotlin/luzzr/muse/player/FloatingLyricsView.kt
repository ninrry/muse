package luzzr.muse.player

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.view.View
import luzzr.muse.domain.lyrics.LyricsTimeline
import luzzr.muse.domain.lyrics.LyricsSyncEngine
import luzzr.muse.domain.model.LrcLine
import kotlin.math.roundToInt

/**
 * Hardware accelerated renderer for the overlay. It deliberately consumes the
 * same [LyricsTimeline] as Compose instead of receiving a preselected line.
 */
class FloatingLyricsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT
    }
    private var timeline: LyricsTimeline = LyricsTimeline(emptyList())
    private var syncEngine = LyricsSyncEngine(timeline)
    private var lyrics: List<LrcLine> = emptyList()
    private var durationMs = 0L
    private var offsetMs = 0L
    private var sourcePositionMs = 0L
    private var anchorPositionMs = 0L
    private var anchorWallClockMs = 0L
    private var isPlaying = false

    fun setPlayback(
        lyrics: List<LrcLine>,
        positionMs: Long,
        durationMs: Long,
        offsetMs: Long,
        isPlaying: Boolean
    ) {
        if (this.lyrics !== lyrics) {
            this.lyrics = lyrics
            timeline = LyricsTimeline(lyrics)
            syncEngine = LyricsSyncEngine(timeline)
        }
        this.durationMs = durationMs
        this.offsetMs = offsetMs
        this.isPlaying = isPlaying
        sourcePositionMs = positionMs.coerceAtLeast(0L)
        anchorPositionMs = sourcePositionMs
        anchorWallClockMs = android.os.SystemClock.elapsedRealtime()
        syncEngine.reset(anchorPositionMs, anchorWallClockMs)
        contentDescription = if (lyrics.isEmpty()) "Floating lyrics: no lyrics" else "Floating lyrics"
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maxWidth = MeasureSpec.getSize(widthMeasureSpec).takeIf { it > 0 } ?: (320 * density).roundToInt()
        val width = resolveSize((320 * density).roundToInt(), widthMeasureSpec).coerceAtMost(maxWidth)
        val height = resolveSize((148 * density).roundToInt(), heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = android.os.SystemClock.elapsedRealtime()
        val frame = syncEngine.frameAt(
            positionMs = sourcePositionMs,
            durationMs = durationMs,
            offsetMs = offsetMs,
            isPlaying = isPlaying,
            wallClockMs = now
        )
        if (lyrics.isEmpty()) {
            drawCentered(canvas, "暂无同步歌词", textSize = 14f, color = COLOR_MUTED, y = height / 2f)
            return
        }
        if (frame.currentIndex < 0) {
            drawCentered(canvas, "等待歌词开始", textSize = 14f, color = COLOR_MUTED, y = height / 2f)
        } else {
            val current = timeline.lines[frame.currentIndex]
            drawLine(canvas, timeline.lines.getOrNull(frame.previousIndex), 20f, COLOR_ADJACENT, 0.0f, 0)
            drawLine(canvas, current, 28f, COLOR_BASE, frame.revealCharacters, 1)
            drawLine(canvas, timeline.lines.getOrNull(frame.nextIndex), 20f, COLOR_ADJACENT, 0.0f, 2)
        }
        if (isPlaying) postInvalidateDelayed(16L)
    }

    private fun drawLine(canvas: Canvas, line: LrcLine?, textSizeSp: Float, color: Int, reveal: Float, slot: Int) {
        if (line == null || line.text.isBlank()) return
        val horizontalPadding = (18 * density).roundToInt()
        val width = (measuredWidth - horizontalPadding * 2).coerceAtLeast(1)
        textPaint.textSize = textSizeSp * density
        textPaint.color = color
        textPaint.typeface = if (textSizeSp > 24f) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        textPaint.setShadowLayer(5f * density, 0f, 2f * density, Color.BLACK)
        val layout = StaticLayout.Builder.obtain(
            line.text,
            0,
            line.text.length,
            textPaint,
            width
        ).setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .setMaxLines(if (textSizeSp > 24f) 2 else 1)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
        val y = when (slot) {
            0 -> 10
            1 -> (height - layout.height) / 2
            else -> height - layout.height - 10
        }
        canvas.save()
        canvas.translate(horizontalPadding.toFloat(), y.toFloat())
        layout.draw(canvas)
        if (reveal > 0f) {
            canvas.save()
            val fraction = (reveal / line.text.length.coerceAtLeast(1)).coerceIn(0f, 1f)
            canvas.clipRect(0f, 0f, width * fraction, layout.height.toFloat())
            textPaint.color = COLOR_FILL
            layout.draw(canvas)
            canvas.restore()
        }
        canvas.restore()
    }

    private fun drawCentered(canvas: Canvas, text: String, textSize: Float, color: Int, y: Float) {
        textPaint.textSize = textSize * density
        textPaint.color = color
        textPaint.typeface = Typeface.DEFAULT
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(text, width / 2f, y, textPaint)
        textPaint.textAlign = Paint.Align.LEFT
    }

    companion object {
        private const val COLOR_BASE = 0xFFF3ECE4.toInt()
        private const val COLOR_FILL = 0xFFFFC878.toInt()
        private const val COLOR_ADJACENT = 0x99B89A77.toInt()
        private const val COLOR_MUTED = 0xCCB89A77.toInt()
    }
}
