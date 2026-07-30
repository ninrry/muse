package luzzr.muse.data.lyrics

import luzzr.muse.domain.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlinx.coroutines.test.runTest

class FileSystemLocalLyricsSourceTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val source = FileSystemLocalLyricsSource()

    @Test
    fun `matching title and artist tags select local LRC`() {
        val directory = temporaryFolder.newFolder("music")
        val audio = File(directory, "歌手 - 歌曲.flac").apply { createNewFile() }
        val lrc = File(directory, "歌手 - 歌曲.lrc").apply {
            writeText(
                """
                [ti:歌曲]
                [ar:歌手]
                [00:01.00]第一句
                """.trimIndent()
            )
        }

        val result = source.findBestMatch(
            song = Song(
                id = 1,
                title = "歌曲",
                artist = "歌手",
                filePath = audio.absolutePath
            ),
            files = listOf(lrc)
        )

        assertEquals("local", result?.source)
        assertEquals("第一句", result?.syncedLines?.single()?.text)
    }

    @Test
    fun `same title with a different artist is rejected`() {
        val directory = temporaryFolder.newFolder("wrong-artist")
        val audio = File(directory, "正确歌手 - 同名歌曲.mp3").apply { createNewFile() }
        val lrc = File(directory, "其他歌手 - 同名歌曲.lrc").apply {
            writeText(
                """
                [ti:同名歌曲]
                [ar:其他歌手]
                [00:01.00]错误歌词
                """.trimIndent()
            )
        }

        val result = source.findBestMatch(
            song = Song(
                id = 2,
                title = "同名歌曲",
                artist = "正确歌手",
                filePath = audio.absolutePath
            ),
            files = listOf(lrc)
        )

        assertNull(result)
    }

    @Test
    fun `filename identity works when LRC has no metadata tags`() {
        val directory = temporaryFolder.newFolder("filename")
        val audio = File(directory, "Artist - Track.m4a").apply { createNewFile() }
        val lrc = File(directory, "Artist - Track.lrc").apply {
            writeText("[00:01.00]Local line")
        }

        val result = source.findBestMatch(
            song = Song(
                id = 3,
                title = "Track",
                artist = "Artist",
                filePath = audio.absolutePath
            ),
            files = listOf(lrc)
        )

        assertEquals("Local line", result?.syncedLines?.single()?.text)
    }

    @Test
    fun `exact sidecar filename is preferred without artist metadata`() = runTest {
        val directory = temporaryFolder.newFolder("exact-sidecar")
        val audio = File(directory, "Track 01.mp3").apply { createNewFile() }
        File(directory, "Track 01.lrc").writeText("[00:01.00]Exact local line")

        val result = source.find(
            Song(
                id = 31,
                title = "Track 01",
                artist = "未知艺术家",
                filePath = audio.absolutePath
            )
        )

        assertEquals("Exact local line", result?.syncedLines?.single()?.text)
        assertEquals("local", result?.source)
    }

    @Test
    fun `enhanced local LRC preserves per-character timing`() {
        val directory = temporaryFolder.newFolder("enhanced")
        val audio = File(directory, "歌手 - 逐字.mp3").apply { createNewFile() }
        val lrc = File(directory, "歌手 - 逐字.lrc").apply {
            writeText(
                """
                [ti:逐字]
                [ar:歌手]
                [00:01.00]<00:01.000>你<00:01.300>好
                """.trimIndent()
            )
        }

        val result = source.findBestMatch(
            song = Song(
                id = 4,
                title = "逐字",
                artist = "歌手",
                filePath = audio.absolutePath
            ),
            files = listOf(lrc)
        )

        val words = result?.syncedLines?.single()?.words.orEmpty()
        assertEquals(listOf("你", "好"), words.map { it.text })
        assertEquals(listOf(1_000L, 1_300L), words.map { it.timeMs })
        assertTrue(result?.rawSyncedLyrics?.contains("[ti:逐字]") == true)
    }
}
