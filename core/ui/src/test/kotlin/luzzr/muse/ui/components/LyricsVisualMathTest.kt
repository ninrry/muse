package luzzr.muse.ui.components

import luzzr.muse.domain.model.WordSegment
import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsVisualMathTest {
    @Test
    fun `focus offset places lyric at forty two percent of viewport`() {
        assertEquals(-370, lyricsFocusScrollOffset(viewportHeightPx = 1_000, itemHeightPx = 100))
    }

    @Test
    fun `glyph reveal follows exact ltr glyph bounds`() {
        assertEquals(
            18f,
            glyphRevealEdge(left = 10f, right = 30f, progress = 0.4f, isRtl = false),
            0.001f
        )
    }

    @Test
    fun `glyph reveal mirrors exact rtl glyph bounds`() {
        assertEquals(
            22f,
            glyphRevealEdge(left = 10f, right = 30f, progress = 0.4f, isRtl = true),
            0.001f
        )
    }

    @Test
    fun `line end protects final timed word and minimum duration`() {
        val words = listOf(WordSegment(text = "尾", timeMs = 1_300L, charStart = 0))

        assertEquals(1_580L, resolveLineEndMs(1_000L, 1_200L, words))
        assertEquals(1_400L, resolveLineEndMs(1_000L, 1_100L, null))
    }
}
