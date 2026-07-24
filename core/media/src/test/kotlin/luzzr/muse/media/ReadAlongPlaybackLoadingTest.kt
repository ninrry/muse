package luzzr.muse.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadAlongPlaybackLoadingTest {
    @Test
    fun `same prepared media does not reload`() {
        assertFalse(
            shouldPrepareReadAlongMedia(
                currentUri = "file:///book/ch001.m4a",
                requestedUri = "file:///book/ch001.m4a",
                isIdle = false
            )
        )
    }

    @Test
    fun `stopped same media prepares before playback`() {
        assertTrue(
            shouldPrepareReadAlongMedia(
                currentUri = "file:///book/ch001.m4a",
                requestedUri = "file:///book/ch001.m4a",
                isIdle = true
            )
        )
    }

    @Test
    fun `new chapter media always prepares`() {
        assertTrue(
            shouldPrepareReadAlongMedia(
                currentUri = "file:///book/ch001.m4a",
                requestedUri = "file:///book/ch002.m4a",
                isIdle = false
            )
        )
    }
}
