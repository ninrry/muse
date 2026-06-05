package luzzr.muse.domain.text

interface TextNormalizer {
    fun toSimplified(text: String): String
}
