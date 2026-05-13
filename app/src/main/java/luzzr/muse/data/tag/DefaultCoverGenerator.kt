package luzzr.muse.data.tag

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import java.io.ByteArrayOutputStream

/**
 * Generates default album artwork as a solid-color background with
 * the song title centered on it.
 *
 * - Color is deterministically chosen from the song title (hash-based)
 * - Title text is truncated to 8 characters
 * - Font size scales based on character count
 * - Output is a 500x500 PNG byte array suitable for jaudiotagger Artwork
 */
object DefaultCoverGenerator {

    private const val COVER_SIZE = 500 // px
    private const val MAX_CHARS = 8

    // Warm, pleasant palette colors (Material You inspired)
    private val PALETTE = intArrayOf(
        0xFFE57373.toInt(), // Red 300
        0xFFF06292.toInt(), // Pink 300
        0xFFBA68C8.toInt(), // Purple 300
        0xFF64B5F6.toInt(), // Blue 300
        0xFF4DD0E1.toInt(), // Cyan 300
        0xFF81C784.toInt(), // Green 300
        0xFFAED581.toInt(), // Light Green 300
        0xFFFFD54F.toInt(), // Yellow 300
        0xFFFFB74D.toInt(), // Orange 300
        0xFFA1887F.toInt(), // Brown 300
        0xFF90A4AE.toInt(), // Blue Grey 300
        0xFFCE93D8.toInt(), // Purple 200
        0xFFEF9A9A.toInt(), // Red 200
        0xFF80CBC4.toInt(), // Teal 300
        0xFFF48FB1.toInt(), // Pink 200
        0xFF7986CB.toInt(), // Indigo 300
    )

    /**
     * Generate a default cover image.
     *
     * @param title Song title (will be truncated to MAX_CHARS)
     * @return PNG byte array of the generated 500x500 cover
     */
    fun generate(title: String): ByteArray {
        val displayText = truncateTitle(title)
        val bgColor = pickColor(title)

        val bitmap = Bitmap.createBitmap(COVER_SIZE, COVER_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Draw background
        canvas.drawColor(bgColor)

        // Draw subtle decorative circle (lighter variant)
        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(20, 255, 255, 255) // very subtle white overlay
            style = Paint.Style.FILL
        }
        canvas.drawCircle(COVER_SIZE * 0.75f, COVER_SIZE * 0.25f, COVER_SIZE * 0.35f, accentPaint)

        // Draw second subtle circle
        canvas.drawCircle(COVER_SIZE * 0.2f, COVER_SIZE * 0.8f, COVER_SIZE * 0.25f, accentPaint)

        // Determine font size based on character count
        val fontSize = when {
            displayText.length <= 2 -> COVER_SIZE * 0.28f
            displayText.length <= 4 -> COVER_SIZE * 0.20f
            displayText.length <= 6 -> COVER_SIZE * 0.16f
            else -> COVER_SIZE * 0.13f
        }

        // Draw shadow text
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(40, 0, 0, 0)
            textSize = fontSize
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }

        // Measure text bounds for centering
        val textBounds = Rect()
        textPaint.getTextBounds(displayText, 0, displayText.length, textBounds)
        val x = COVER_SIZE / 2f
        val y = COVER_SIZE / 2f - textBounds.exactCenterY()

        // Draw shadow offset
        canvas.drawText(displayText, x + 3f, y + 3f, textPaint)

        // Draw main text
        textPaint.apply {
            color = Color.WHITE
            textSize = fontSize
            typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText(displayText, x, y, textPaint)

        // Convert to PNG bytes
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        bitmap.recycle()

        return stream.toByteArray()
    }

    /**
     * Truncate title to MAX_CHARS, adding "…" if truncated.
     */
    private fun truncateTitle(title: String): String {
        val cleaned = title.trim()
        return if (cleaned.length > MAX_CHARS) {
            cleaned.take(MAX_CHARS - 1) + "…"
        } else {
            cleaned.ifEmpty { "♪" }
        }
    }

    /**
     * Deterministically pick a color from PALETTE based on the title hash.
     */
    private fun pickColor(title: String): Int {
        val hash = title.hashCode()
        val idx = ((hash % PALETTE.size) + PALETTE.size) % PALETTE.size
        return PALETTE[idx]
    }
}
