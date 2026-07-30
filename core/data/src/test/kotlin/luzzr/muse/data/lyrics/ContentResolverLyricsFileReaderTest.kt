package luzzr.muse.data.lyrics

import android.content.ContentResolver
import android.content.Context
import io.mockk.every
import io.mockk.mockk
import java.io.ByteArrayInputStream
import kotlinx.coroutines.test.runTest
import luzzr.muse.domain.lyrics.LyricsFileReadResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ContentResolverLyricsFileReaderTest {

    private val context: Context = mockk()
    private val contentResolver: ContentResolver = mockk()
    private lateinit var reader: ContentResolverLyricsFileReader

    @Before
    fun setUp() {
        every { context.contentResolver } returns contentResolver
        reader = ContentResolverLyricsFileReader(context)
    }

    @Test
    fun `reads and decodes content URI`() = runTest {
        val content = "[00:01.00]本地歌词"
        every { contentResolver.openInputStream(any()) } returns
            ByteArrayInputStream(content.toByteArray(Charsets.UTF_8))

        val result = reader.read("content://lyrics/picked")

        assertEquals(content, (result as LyricsFileReadResult.Success).content)
    }

    @Test
    fun `rejects documents larger than limit`() = runTest {
        every { contentResolver.openInputStream(any()) } returns
            ByteArrayInputStream(ByteArray(MAX_LRC_FILE_BYTES.toInt() + 1))

        val result = reader.read("content://lyrics/large")

        assertSame(LyricsFileReadResult.TooLarge, result)
    }

    @Test
    fun `rejects non content URI`() = runTest {
        val result = reader.read("file:///storage/emulated/0/Music/song.lrc")

        assertSame(LyricsFileReadResult.Unreadable, result)
    }
}
