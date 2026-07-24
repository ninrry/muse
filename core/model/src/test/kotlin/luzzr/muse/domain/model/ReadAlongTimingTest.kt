package luzzr.muse.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadAlongTimingTest {
    @Test
    fun `zero duration unit is skipped and valid unit at same start wins`() {
        val units = listOf(
            ReadAlongUnit("一", 8000L, 8000L),
            ReadAlongUnit("条", 8000L, 8160L),
            ReadAlongUnit("水", 8160L, 8400L)
        )

        assertEquals(1, readAlongActiveUnitIndex(units, 8000L))
        assertEquals(2, readAlongActiveUnitIndex(units, 8160L))
    }

    @Test
    fun `gap keeps previous valid unit and before first unit returns no match`() {
        val units = listOf(
            ReadAlongUnit("轻", 800L, 960L),
            ReadAlongUnit("我", 1760L, 1840L)
        )

        assertEquals(-1, readAlongActiveUnitIndex(units, 400L))
        assertEquals(0, readAlongActiveUnitIndex(units, 1200L))
        assertEquals(1, readAlongActiveUnitIndex(units, 1760L))
    }

    @Test
    fun `reading navigation skips retained front matter and preserves legacy fallback`() {
        val frontMatterBook = book(listOf(false, false, false, true, true))

        assertEquals(listOf(3, 4), frontMatterBook.readingChapterIndices)
        assertEquals(2, frontMatterBook.readingChapterCount)
        assertEquals(null, frontMatterBook.previousReadingChapterIndex(3))
        assertEquals(4, frontMatterBook.nextReadingChapterIndex(3))
        assertEquals(3, frontMatterBook.previousReadingChapterIndex(4))
        assertEquals(1, frontMatterBook.readingChapterOrdinal(3))
        assertEquals(2, frontMatterBook.readingChapterOrdinal(4))

        val legacyBook = book(listOf(false, false))
        assertEquals(listOf(0, 1), legacyBook.readingChapterIndices)
        assertEquals(1, legacyBook.nextReadingChapterIndex(0))
    }

    private fun book(readingFlags: List<Boolean>): ReadAlongBook = ReadAlongBook(
        id = "book",
        title = "测试书",
        author = "作者",
        epubPath = "/tmp/book.epub",
        packageRoot = "/tmp/book",
        coverPath = null,
        chapters = readingFlags.mapIndexed { index, isReading ->
            ReadAlongChapter(
                id = "ch$index",
                title = "第${index + 1}章",
                index = index,
                href = "chapter-$index.xhtml",
                htmlPath = "/tmp/chapter-$index.xhtml",
                audioPath = if (isReading) "/tmp/chapter-$index.m4a" else null,
                audioDurationMs = 1_000L,
                sourceChars = 100,
                isReadingContent = isReading,
                hasAlignment = isReading
            )
        },
        toc = emptyList(),
        isSynchronized = true,
        sourceFingerprint = "fingerprint",
        createdAt = 0L,
        updatedAt = 0L
    )
}
