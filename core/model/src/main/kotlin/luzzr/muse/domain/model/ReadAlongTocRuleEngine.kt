package luzzr.muse.domain.model

/** A chapter-title rule that can be shipped in a read-along manifest. */
data class ReadAlongTocRule(
    val name: String,
    val pattern: String
)

data class ReadAlongHeading(
    val title: String,
    val ruleName: String
)

/**
 * Conservative, line-oriented chapter heading detection inspired by the
 * fallback rules used by Legado and Readest. Native EPUB nav/NCX remains the
 * primary source; this engine is only used when an EPUB does not provide one.
 */
object ReadAlongTocRuleEngine {
    val defaultRules: List<ReadAlongTocRule> = listOf(
        ReadAlongTocRule(
            name = "中文章节",
            pattern = "^\\s*第[ 　零〇一二三四五六七八九十百千万0-9]+[章节回讲篇话](?:[：:、 　()（）0-9]*[^\\n-]{0,60})?\\s*$"
        ),
        ReadAlongTocRule(
            name = "中文序章与番外",
            pattern = "^\\s*(?:楔子|前言|简介|引言|序言|序章|总论|概论|后记|尾声|番外篇?|外传)(?:[：: 　][^\\n-]{0,60})?\\s*$"
        ),
        ReadAlongTocRule(
            name = "英文章节",
            pattern = "^\\s*(?:chapter|part|section|book|volume|act)\\s+(?:[0-9]+|[ivxlcdm]+)(?:[-：:. 　][^\\n-]{0,60})?\\s*$"
        ),
        ReadAlongTocRule(
            name = "多级数字标题",
            pattern = "^\\s*\\d+(?:\\.\\d+){1,3}[ 　.]?[^\\n]{1,80}\\s*$"
        ),
        ReadAlongTocRule(
            name = "英文序章",
            pattern = "^\\s*(?:prologue|epilogue|foreword|preface|introduction|afterword)(?:[-：:. 　][^\\n-]{0,60})?\\s*$"
        )
    )

    fun compile(customPatterns: List<String>): List<Pair<ReadAlongTocRule, Regex>> {
        val custom = customPatterns.mapIndexedNotNull { index, pattern ->
            val cleaned = pattern.trim()
            if (cleaned.isBlank()) return@mapIndexedNotNull null
            runCatching {
                ReadAlongTocRule("自定义规则 ${index + 1}", cleaned) to Regex(
                    cleaned,
                    setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)
                )
            }.getOrNull()
        }
        val source = if (custom.isEmpty()) {
            defaultRules.mapNotNull { rule ->
                runCatching {
                    rule to Regex(rule.pattern, setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
                }.getOrNull()
            }
        } else {
            custom
        }
        return source
    }

    fun match(title: String, customPatterns: List<String> = emptyList()): ReadAlongHeading? {
        val normalized = title.replace(Regex("\\s+"), " ").trim()
        if (normalized.isBlank()) return null
        val rule = compile(customPatterns).firstOrNull { (_, regex) -> regex.matches(normalized) }
        return rule?.first?.let { ReadAlongHeading(normalized, it.name) }
    }

    fun isLikelyHeading(title: String, tagName: String, customPatterns: List<String> = emptyList()): Boolean {
        val normalizedTag = tagName.lowercase()
        if (normalizedTag in setOf("h1", "h2")) return true
        return match(title, customPatterns) != null
    }
}
