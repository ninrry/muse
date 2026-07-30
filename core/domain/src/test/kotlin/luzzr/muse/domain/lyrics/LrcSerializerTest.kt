package luzzr.muse.domain.lyrics

import luzzr.muse.domain.model.LrcLine
import luzzr.muse.domain.model.WordSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcSerializerTest {

    @Test
    fun `serialize preserves enhanced word timestamps and character ranges`() {
        val source = listOf(
            LrcLine(
                timestamp = 1_000L,
                text = "Hello world",
                words = listOf(
                    WordSegment("Hello", 1_100L, 0),
                    WordSegment("world", 1_800L, 6)
                )
            )
        )

        val serialized = LrcSerializer.serialize(source, offsetMs = 100L)
        val reparsed = LrcParser.parse(serialized).single()

        assertTrue(serialized.contains("<00:01.000>Hello"))
        assertTrue(serialized.contains("<00:01.700>world"))
        assertEquals(source[0].text, reparsed.text)
        assertEquals(listOf(1_000L, 1_700L), reparsed.words.orEmpty().map { it.timeMs })
        assertEquals(listOf(0, 6), reparsed.words.orEmpty().map { it.charStart })
    }

    @Test
    fun `serialize preserves exact word durations`() {
        val source = listOf(
            LrcLine(
                timestamp = 1_000L,
                text = "你好",
                words = listOf(
                    WordSegment("你", 1_000L, 0, durationMs = 160L),
                    WordSegment("好", 1_160L, 1, durationMs = 420L)
                )
            )
        )

        val serialized = LrcSerializer.serialize(source)
        val reparsed = LrcParser.parse(serialized).single()

        assertTrue(serialized.contains("<0,160,0>你"))
        assertTrue(serialized.contains("<160,420,0>好"))
        assertEquals(listOf(1_000L, 1_160L), reparsed.words.orEmpty().map { it.timeMs })
        assertEquals(listOf(160L, 420L), reparsed.words.orEmpty().map { it.durationMs })
    }
}
