package luzzr.muse.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ReadAlongTocRuleEngineTest {
    @Test
    fun `default rules recognize common Chinese and English headings`() {
        assertNotNull(ReadAlongTocRuleEngine.match("第一章 初见"))
        assertNotNull(ReadAlongTocRuleEngine.match("Chapter IV · The Road"))
        assertNotNull(ReadAlongTocRuleEngine.match("2.3 目录解析"))
        assertNotNull(ReadAlongTocRuleEngine.match("番外：冬日"))
        assertNull(ReadAlongTocRuleEngine.match("这是一段普通正文，不是标题。"))
    }

    @Test
    fun `custom patterns replace defaults and invalid patterns are ignored`() {
        assertNull(ReadAlongTocRuleEngine.match("第一章 初见", listOf("^卷[一二三]$")))
        assertEquals("自定义规则 1", ReadAlongTocRuleEngine.match("卷一", listOf("^卷[一二三]$"))?.ruleName)
        assertNotNull(ReadAlongTocRuleEngine.match("第一章 初见", listOf("[")))
    }

    @Test
    fun `h1 and h2 are accepted as fallback headings`() {
        assertEquals(true, ReadAlongTocRuleEngine.isLikelyHeading("任意标题", "h1"))
        assertEquals(true, ReadAlongTocRuleEngine.isLikelyHeading("Chapter 1", "h3"))
        assertEquals(false, ReadAlongTocRuleEngine.isLikelyHeading("普通段落", "p"))
    }
}
