package luzzr.muse.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import luzzr.muse.domain.model.LrcLine

class FloatingLyricsStateHolderTest {

    @Test
    fun `disabling overlay also clears lock and controls`() {
        val state = FloatingLyricsStateHolder()

        state.updateFloatingLyricsEnabled(true)
        state.updateLocked(true)
        state.updateControlsVisible(true)
        state.updateFloatingLyricsEnabled(false)

        assertFalse(state.floatingLyricsEnabled.value)
        assertFalse(state.isLocked.value)
        assertFalse(state.controlsVisible.value)
    }

    @Test
    fun `empty lyrics cannot retain the previous current line`() {
        val state = FloatingLyricsStateHolder()

        state.updateCurrentLyrics(listOf(LrcLine(0, "line")))
        state.updateCurrentLyricLine(0)
        state.updateCurrentLyrics(emptyList())

        assertEquals(-1, state.currentLyricLine.value)
    }

    @Test
    fun `offset and control state are observable`() {
        val state = FloatingLyricsStateHolder()

        state.updateLyricsOffset(1_250)
        state.updateControlsVisible(true)

        assertEquals(1_250, state.lyricsOffsetMs.value)
        assertTrue(state.controlsVisible.value)
    }
}
