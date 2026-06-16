package luzzr.muse.data.scanner

import android.content.Context
import android.database.MatrixCursor
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MediaStoreScannerTest {

    private val scanner = MediaStoreScanner(mockk<Context>(relaxed = true))

    @Test
    fun `readSongsFromCursor filters video mp4 rows`() {
        val cursor = MatrixCursor(scanner.projection).apply {
            addMediaRow(
                id = 1L,
                path = "/storage/emulated/0/Download/movie.mp4",
                mime = "video/mp4"
            )
        }

        val songs = cursor.use { scanner.readSongsFromCursor(it) }

        assertTrue(songs.isEmpty())
    }

    @Test
    fun `readSongsFromCursor keeps m4a audio rows`() {
        val cursor = MatrixCursor(scanner.projection).apply {
            addMediaRow(
                id = 2L,
                path = "/storage/emulated/0/Music/song.m4a",
                mime = "audio/mp4"
            )
        }

        val songs = cursor.use { scanner.readSongsFromCursor(it) }

        assertEquals(1, songs.size)
        assertEquals("M4A/AAC", songs.single().codec)
    }

    private fun MatrixCursor.addMediaRow(id: Long, path: String, mime: String) {
        addRow(
            arrayOf<Any?>(
                id,
                "标题",
                "歌手",
                "专辑",
                10L,
                180_000L,
                path,
                mime,
                1,
                2026,
                4096L,
                1_700_000_000L,
                1_700_000_100L
            )
        )
    }
}
