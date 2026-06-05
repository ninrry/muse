package luzzr.muse.data.tag

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.core.graphics.createBitmap
import java.io.ByteArrayOutputStream

object DefaultCoverGenerator {

    private const val COVER_SIZE = 500
    private const val MAX_CHARS = 8

    private val PALETTE = intArrayOf(
        0xFF8B7355.toInt(),
        0xFF9E8E7C.toInt(),
        0xFFC49A6C.toInt(),
        0xFFA1887F.toInt(),
        0xFF90A4AE.toInt()
    )

    fun generate(title: String): ByteArray {
        val displayText = truncateTitle(title)
        val bgColor = pickColor(title)

        val bitmap = createBitmap(COVER_SIZE, COVER_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        canvas.drawColor(bgColor)

        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(20, 255, 255, 255)
            style = Paint.Style.FILL
        }
        canvas.drawCircle(COVER_SIZE * 0.75f, COVER_SIZE * 0.25f, COVER_SIZE * 0.35f, accentPaint)
        canvas.drawCircle(COVER_SIZE * 0.2f, COVER_SIZE * 0.8f, COVER_SIZE * 0.25f, accentPaint)

        val fontSize = when {
            displayText.length <= 2 -> COVER_SIZE * 0.28f
            displayText.length <= 4 -> COVER_SIZE * 0.20f
            displayText.length <= 6 -> COVER_SIZE * 0.16f
            else -> COVER_SIZE * 0.13f
        }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(40, 0, 0, 0)
            textSize = fontSize
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }

        val textBounds = Rect()
        textPaint.getTextBounds(displayText, 0, displayText.length, textBounds)
        val x = COVER_SIZE / 2f
        val y = COVER_SIZE / 2f - textBounds.exactCenterY()

        canvas.drawText(displayText, x + 3f, y + 3f, textPaint)

        textPaint.apply {
            color = Color.WHITE
            textSize = fontSize
            typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText(displayText, x, y, textPaint)

        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        bitmap.recycle()

        return stream.toByteArray()
    }

    private fun truncateTitle(title: String): String {
        val cleaned = title.trim()
        return if (cleaned.length > MAX_CHARS) {
            cleaned.take(MAX_CHARS - 1) + "\u2026"
        } else {
            cleaned.ifEmpty { " " }
        }
    }

    private fun pickColor(title: String): Int {
        val hash = title.hashCode()
        val idx = ((hash % PALETTE.size) + PALETTE.size) % PALETTE.size
        return PALETTE[idx]
    }
}
