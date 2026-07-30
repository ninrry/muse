package luzzr.muse.data.network

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test

class NeteaseLyricsSourceTest {

    private val source = NeteaseLyricsSource(OkHttpClient())

    @Test
    fun `word timed YRC is preferred over line LRC`() {
        val yrc = "[28480,11820](28480,160,0)我(28640,420,0)带"

        assertEquals(
            yrc,
            source.selectPreferredSyncedLyrics(yrc, "[00:28.480]我带")
        )
    }

    @Test
    fun `line LRC is used when YRC is unavailable`() {
        assertEquals(
            "[00:01.000]歌词",
            source.selectPreferredSyncedLyrics(null, "[00:01.000]歌词")
        )
    }

    @Test
    fun `line LRC is used when YRC has no valid word timing`() {
        assertEquals(
            "[00:01.000]歌词",
            source.selectPreferredSyncedLyrics("invalid", "[00:01.000]歌词")
        )
    }
}
