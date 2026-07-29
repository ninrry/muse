package luzzr.muse.media

import luzzr.muse.domain.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PlaybackStateTest {

    @Test
    fun `default state is idle and repeat all`() {
        val state = PlaybackState()

        assertFalse(state.isPlaying)
        assertEquals(PlaybackRepeatMode.ALL, state.repeatMode)
        assertEquals(0L, state.positionMs)
        assertEquals(emptyList<Song>(), state.playlist)
    }

    @Test
    fun `copy creates a new immutable snapshot`() {
        val original = PlaybackState()
        val playing = original.copy(isPlaying = true, positionMs = 1_000L)

        assertFalse(original.isPlaying)
        assertEquals(0L, original.positionMs)
        assertEquals(1_000L, playing.positionMs)
    }
}
