package luzzr.muse.domain.lyrics

import luzzr.muse.domain.model.LrcLine
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [LrcParser] — covers timestamp parsing, text cleaning,
 * short-line merging, and line-index lookup.
 */
class LrcParserTest {

    // -- Basic parsing ----------------------------------------------

    @Test
    fun `parse standard LRC with mm ss xx timestamps`() {
        val lrc = """
            [00:12.34]Hello world
            [01:05.67]Second line
        """.trimIndent()

        val lines = LrcParser.parse(lrc)
        assertEquals(2, lines.size)
        assertEquals(12_340L, lines[0].timestamp)
        assertEquals("Hello world", lines[0].text)
        assertEquals(65_670L, lines[1].timestamp)
        assertEquals("Second line", lines[1].text)
    }

    @Test
    fun `parse LRC with mm ss xxx timestamps`() {
        val lrc = "[02:30.123]Millisecond line"
        val lines = LrcParser.parse(lrc)
        assertEquals(1, lines.size)
        assertEquals(150_123L, lines[0].timestamp)
    }

    @Test
    fun `parse retains blank text lines as instrumental breaks`() {
        val lrc = """
            [00:05.00]First
            [00:10.00]
            [00:15.00]Third
        """.trimIndent()

        val lines = LrcParser.parse(lrc)
        assertEquals(3, lines.size)
        assertEquals("First", lines[0].text)
        assertEquals("", lines[1].text)
        assertEquals(10_000L, lines[1].timestamp)
        assertEquals("Third", lines[2].text)
    }

    @Test
    fun `parse LRC with optional milliseconds`() {
        val lrc = """
            [00:12]Hello standard no ms
            [01:05.6]Single digit ms
        """.trimIndent()

        val lines = LrcParser.parse(lrc)
        assertEquals(2, lines.size)
        assertEquals(12_000L, lines[0].timestamp)
        assertEquals("Hello standard no ms", lines[0].text)
        assertEquals(65_600L, lines[1].timestamp)
        assertEquals("Single digit ms", lines[1].text)
    }

    @Test
    fun `parse handles empty input`() {
        val lines = LrcParser.parse("")
        assertTrue(lines.isEmpty())
    }

    @Test
    fun `parse handles lines without timestamps`() {
        val lrc = """
            [00:05.00]Valid
            This line has no timestamp
            [00:10.00]Also valid
        """.trimIndent()

        val lines = LrcParser.parse(lrc)
        assertEquals(2, lines.size)
    }

    // -- Dual timestamps -------------------------------------------

    @Test
    fun `parse dual timestamps on same line creates two entries`() {
        val lrc = "[00:05.00][00:30.00]Repeated line"
        val lines = LrcParser.parse(lrc)
        assertEquals(2, lines.size)
        assertEquals(5_000L, lines[0].timestamp)
        assertEquals(30_000L, lines[1].timestamp)
        assertEquals("Repeated line", lines[0].text)
        assertEquals("Repeated line", lines[1].text)
    }

    // -- Sub-timestamp cleaning -------------------------------------

    @Test
    fun `parse strips embedded word-level sub-timestamps`() {
        val lrc = "[00:05.00]<00:05.100>Hello <00:05.500>world"
        val lines = LrcParser.parse(lrc)
        assertEquals(1, lines.size)
        assertEquals("Hello world", lines[0].text)
        assertEquals(2, lines[0].words?.size)
        assertEquals(5_100L, lines[0].words?.get(0)?.timeMs)
        assertEquals(0, lines[0].words?.get(0)?.charStart)
        assertEquals(5, lines[0].words?.get(0)?.charEndExclusive)
        assertEquals(6, lines[0].words?.get(1)?.charStart)
    }

    @Test
    fun `parse KRC and QRC duration word markers as absolute word times`() {
        val krc = "[1000,3000](0,500,0)你(500,500,0)好"
        val qrc = "[00:01.00]<0,500,0>你<500,500,0>好"

        val krcLine = LrcParser.parse(krc).single()
        val qrcLine = LrcParser.parse(qrc).single()

        assertEquals("你好", krcLine.text)
        assertEquals(listOf(1_000L, 1_500L), krcLine.words.orEmpty().map { it.timeMs })
        assertEquals(listOf(1_000L, 1_500L), qrcLine.words.orEmpty().map { it.timeMs })
    }

    // -- Short-line merging -----------------------------------------

    @Test
    fun `parse merges consecutive short lines within time window`() {
        // Simulates word-level karaoke LRC with ≤2 chars per line
        val lrc = """
            [00:01.00]我
            [00:01.20]是
            [00:01.40]中
            [00:01.60]国
            [00:05.00]Normal line
        """.trimIndent()

        val lines = LrcParser.parse(lrc)
        // First 4 short lines should be merged into one
        assertTrue("Expected merged line, got ${lines.size} lines", lines.size <= 3)
        assertEquals("我是中国", lines[0].text)
        assertEquals(listOf(1_000L, 1_200L, 1_400L, 1_600L), lines[0].words.orEmpty().map { it.timeMs })
        assertEquals(listOf(0, 1, 2, 3), lines[0].words.orEmpty().map { it.charStart })
    }

    @Test
    fun `parse does not merge long lines`() {
        val lrc = """
            [00:01.00]Hello world this is long
            [00:01.20]Another long line here
        """.trimIndent()

        val lines = LrcParser.parse(lrc)
        assertEquals(2, lines.size)
    }

    // -- Sorting ----------------------------------------------------

    @Test
    fun `parse sorts lines by timestamp`() {
        val lrc = """
            [00:30.00]Second
            [00:05.00]First
        """.trimIndent()

        val lines = LrcParser.parse(lrc)
        assertEquals(2, lines.size)
        assertEquals("First", lines[0].text)
        assertEquals("Second", lines[1].text)
    }

    // -- getLineIndex -----------------------------------------------

    @Test
    fun `getLineIndex returns correct index for position`() {
        val lines = listOf(
            LrcLine(5_000, "First"),
            LrcLine(15_000, "Second"),
            LrcLine(30_000, "Third")
        )

        assertEquals(-1, LrcParser.getLineIndex(lines, 0))
        assertEquals(0, LrcParser.getLineIndex(lines, 5_000))
        assertEquals(0, LrcParser.getLineIndex(lines, 10_000))
        assertEquals(1, LrcParser.getLineIndex(lines, 15_000))
        assertEquals(1, LrcParser.getLineIndex(lines, 20_000))
        assertEquals(2, LrcParser.getLineIndex(lines, 30_000))
        assertEquals(2, LrcParser.getLineIndex(lines, 60_000))
    }

    @Test
    fun `getLineIndex returns -1 for empty list`() {
        assertEquals(-1, LrcParser.getLineIndex(emptyList(), 1000))
    }
}
