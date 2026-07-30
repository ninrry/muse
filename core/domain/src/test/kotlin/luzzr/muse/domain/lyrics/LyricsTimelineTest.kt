package luzzr.muse.domain.lyrics

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import luzzr.muse.domain.model.LrcLine
import luzzr.muse.domain.model.WordSegment

class LyricsTimelineTest {

    @Test
    fun `frame before first line exposes next line`() {
        val timeline = LyricsTimeline(
            listOf(LrcLine(1_000, "one"), LrcLine(2_000, "two"))
        )

        val frame = timeline.frameAt(500)

        assertEquals(-1, frame.currentIndex)
        assertEquals(0, frame.nextIndex)
    }

    @Test
    fun `duplicate identical lines and blank lines are normalized`() {
        val timeline = LyricsTimeline(
            listOf(
                LrcLine(2_000, " second "),
                LrcLine(1_000, " "),
                LrcLine(2_000, "second"),
                LrcLine(1_000, "first")
            )
        )

        assertEquals(listOf("first", "second"), timeline.lines.map { it.text })
        assertEquals(1, timeline.indexAt(2_000))
    }

    @Test
    fun `last line uses song duration when available`() {
        val timeline = LyricsTimeline(listOf(LrcLine(1_000, "last")))

        assertEquals(5_000, timeline.lineEndMs(0, durationMs = 5_000))
        assertEquals(1f, timeline.frameAt(5_000, durationMs = 5_000).lineProgress)
    }

    @Test
    fun `valid word timing produces continuous reveal`() {
        val line = LrcLine(
            timestamp = 1_000,
            text = "hello world",
            words = listOf(
                WordSegment("hello", 1_000, 0),
                WordSegment("world", 2_000, 6)
            )
        )
        val timeline = LyricsTimeline(listOf(line, LrcLine(4_000, "next")))

        val frame = timeline.frameAt(1_500)

        assertTrue(frame.revealCharacters > 0f)
        assertTrue(frame.revealCharacters < line.text.length)
        assertEquals(LyricsFillMode.WORD, frame.fillMode)
        assertEquals(0, frame.activeWordIndex)
    }

    @Test
    fun `explicit word duration completes before next word starts`() {
        val line = LrcLine(
            timestamp = 1_000,
            text = "你好",
            words = listOf(
                WordSegment("你", 1_000, 0, durationMs = 100),
                WordSegment("好", 2_000, 1, durationMs = 500)
            )
        )
        val timeline = LyricsTimeline(listOf(line, LrcLine(4_000, "next")))

        assertEquals(1f, timeline.frameAt(1_150).revealCharacters)
        assertEquals(1.5f, timeline.frameAt(2_250).revealCharacters)
    }

    @Test
    fun `last word duration defines word timed final line end`() {
        val line = LrcLine(
            timestamp = 1_000,
            text = "你",
            words = listOf(WordSegment("你", 1_500, 0, durationMs = 300))
        )
        val timeline = LyricsTimeline(listOf(line))

        assertEquals(1_800L, timeline.lineEndMs(0, durationMs = 10_000))
        assertEquals(1f, timeline.frameAt(1_800, durationMs = 10_000).revealCharacters)
    }

    @Test
    fun `line without words uses explicit line fill fallback`() {
        val timeline = LyricsTimeline(listOf(LrcLine(1_000, "hello"), LrcLine(5_000, "next")))

        val frame = timeline.frameAt(2_000)

        assertEquals(LyricsFillMode.LINE, frame.fillMode)
        assertEquals(-1, frame.activeWordIndex)
        assertTrue(frame.revealCharacters > 0f)
    }

    @Test
    fun `invalid word range falls back to line progress`() {
        val timeline = LyricsTimeline(
            listOf(
                LrcLine(
                    1_000,
                    "hello",
                    listOf(WordSegment("world", 1_000, 0))
                ),
                LrcLine(5_000, "next")
            )
        )

        val frame = timeline.frameAt(2_000)

        assertEquals(0.25f, frame.lineProgress)
        assertEquals(1.25f, frame.revealCharacters)
    }

    @Test
    fun `sync engine does not project while paused and reanchors after seek`() {
        val timeline = LyricsTimeline(listOf(LrcLine(0, "line")))
        val engine = LyricsSyncEngine(timeline)

        val playing = engine.frameAt(100, isPlaying = true, wallClockMs = 100)
        val projected = engine.frameAt(100, isPlaying = true, wallClockMs = 600)
        val paused = engine.frameAt(100, isPlaying = false, wallClockMs = 1_000)
        val seeked = engine.frameAt(5_000, isPlaying = true, wallClockMs = 1_100)

        assertEquals(100, playing.positionMs)
        assertEquals(600, projected.positionMs)
        assertEquals(100, paused.positionMs)
        assertEquals(5_000, seeked.positionMs)
    }
}
