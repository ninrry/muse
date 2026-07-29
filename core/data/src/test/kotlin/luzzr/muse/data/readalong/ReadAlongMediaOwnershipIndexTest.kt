package luzzr.muse.data.readalong

import java.io.File
import kotlin.io.path.createTempDirectory
import luzzr.muse.data.database.ReadAlongBookEntity
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReadAlongMediaOwnershipIndexTest {
    @Test
    fun `excludes identical external source copy but keeps same size unrelated music`() {
        val root = createTempDirectory("muse-readalong-ownership-").toFile()
        try {
            val managedAudio = File(root, "managed/ch001.m4a").apply {
                parentFile?.mkdirs()
                writeBytes(byteArrayOf(1, 2, 3, 4))
            }
            val sourceCopy = File(root, "Download/ch001.m4a").apply {
                parentFile?.mkdirs()
                writeBytes(managedAudio.readBytes())
            }
            val unrelatedSong = File(root, "Music/song.m4a").apply {
                parentFile?.mkdirs()
                writeBytes(byteArrayOf(9, 8, 7, 6))
            }
            val chapters = """[
                {"id":"ch001","audioPath":${JSONObject.quote(managedAudio.absolutePath)},"audioByteSize":4}
            ]""".trimIndent()
            val index = ReadAlongMediaOwnershipIndex.fromBooks(listOf(book(chapters)))

            assertTrue(index.owns(sourceCopy))
            assertFalse(index.owns(unrelatedSong))
        } finally {
            root.deleteRecursively()
        }
    }

    private fun book(chaptersJson: String) = ReadAlongBookEntity(
        id = "book",
        title = "测试书",
        author = "作者",
        language = "zh",
        publisher = "",
        isbn = "",
        description = "",
        pubDate = "",
        tags = "",
        wordCount = 0,
        totalChapters = 1,
        epubPath = "/tmp/book.epub",
        packageRoot = "/tmp/book",
        coverPath = null,
        chaptersJson = chaptersJson,
        tocJson = "[]",
        sourceFingerprint = "fingerprint",
        hasAlignment = true,
        createdAt = 0L,
        updatedAt = 0L
    )
}
