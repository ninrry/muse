package luzzr.muse.data.readalong

import luzzr.muse.domain.model.ReadAlongSentence
import luzzr.muse.domain.model.ReadAlongUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadAlongTextIndexResolverTest {
    @Test
    fun `anchors repeated sentences in chronological order`() {
        val index = buildReadAlongTextIndex(
            chapterHref = "chapter.xhtml",
            plainText = "甲。乙。甲。",
            sentences = listOf(
                sentence("s1", "甲。", listOf("甲", "。")),
                sentence("s2", "乙。", listOf("乙", "。")),
                sentence("s3", "甲。", listOf("甲", "。"))
            )
        )

        assertEquals(listOf(0..1, 2..3, 4..5), index.sentenceRanges)
        assertEquals(listOf(0..0, 1..1, 2..2, 3..3, 4..4, 5..5), index.unitRanges)
    }

    @Test
    fun `unresolved sentence preserves later unit indices`() {
        val index = buildReadAlongTextIndex(
            chapterHref = "chapter.xhtml",
            plainText = "甲乙",
            sentences = listOf(
                sentence("missing", "丙", listOf("丙")),
                sentence("matched", "甲乙", listOf("甲", "乙"))
            )
        )

        assertEquals(2, index.sentenceRanges.size)
        assertTrue(index.sentenceRanges[0].isEmpty())
        assertEquals(0..1, index.sentenceRanges[1])
        assertEquals(3, index.unitRanges.size)
        assertTrue(index.unitRanges[0].isEmpty())
        assertEquals(0..0, index.unitRanges[1])
        assertEquals(1..1, index.unitRanges[2])
    }

    @Test
    fun `expands legacy closing quote for the observed dialogue sentence`() {
        val legacyQuote = "对此，天子生前不是没有自知之明。在他三十一岁那一年，有一天，为了不使臣下惊慌失措，他故意作出放松的姿态询问近侍大臣爰延：“依卿看来，朕是什么样的君主？"
        val plainText = "${legacyQuote}\n”爰延答道。"
        val index = buildReadAlongTextIndex(
            chapterHref = "chapter.xhtml",
            plainText = plainText,
            sentences = listOf(sentence("s1", legacyQuote, emptyList()))
        )

        assertEquals(0..plainText.indexOf('”'), index.sentenceRanges.single())
    }

    @Test
    fun `does not absorb unrelated closing quote after a complete sentence`() {
        val plainText = "第一句。 ”第二句。"
        val index = buildReadAlongTextIndex(
            chapterHref = "chapter.xhtml",
            plainText = plainText,
            sentences = listOf(sentence("s1", "第一句。", emptyList()))
        )

        assertEquals(0..plainText.indexOf('。'), index.sentenceRanges.single())
    }

    private fun sentence(id: String, quote: String, units: List<String>) = ReadAlongSentence(
        id = id,
        chapterId = "ch001",
        sourceText = quote,
        spokenText = quote,
        epubHref = "chapter.xhtml",
        elementId = null,
        elementPath = null,
        chapterCharStart = 0,
        chapterCharEnd = quote.length,
        quoteExact = quote,
        chapterStartMs = 0L,
        chapterEndMs = 1_000L,
        units = units.mapIndexed { index, text ->
            ReadAlongUnit(text, index * 100L, (index + 1) * 100L)
        }
    )
}
