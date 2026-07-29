package luzzr.muse.data.readalong

import luzzr.muse.domain.model.ReadAlongSentence
import luzzr.muse.domain.model.ReadAlongTextIndex

/**
 * Resolves alignment quotes to the exact raw-text coordinates used by WebView.
 *
 * Alignment records are chronological. Searching from the previous resolved
 * sentence avoids mapping repeated prose to the first occurrence. Every source
 * sentence and unit keeps one output slot; an unresolved record uses
 * [IntRange.EMPTY] instead of shifting every later playback index.
 */
internal fun buildReadAlongTextIndex(chapterHref: String, plainText: String, sentences: List<ReadAlongSentence>): ReadAlongTextIndex {
    val canonical = canonicalize(plainText)
    val plainLength = plainText.length
    val sentenceStartByChar = IntArray(plainLength) { -1 }
    val unitStartByChar = IntArray(plainLength) { -1 }
    val sentenceRanges = ArrayList<IntRange>(sentences.size)
    val unitRanges = ArrayList<IntRange>(sentences.sumOf { it.units.size })
    var sentenceCursor = 0

    sentences.forEach { sentence ->
        val quote = sentence.quoteExact.ifBlank { sentence.sourceText }
        val resolvedSentence = canonical.find(quote, sentenceCursor)
        if (resolvedSentence == null) {
            sentenceRanges += IntRange.EMPTY
            repeat(sentence.units.size) { unitRanges += IntRange.EMPTY }
            return@forEach
        }

        val sentenceRange = expandTrailingClosingQuote(
            plainText = plainText,
            alignmentQuote = quote,
            resolvedRange = resolvedSentence.rawRange
        )
        // Clamp start to never overlap the previous sentence's end.
        val clampedStart = if (sentenceRanges.isNotEmpty()) {
            val lastEnd = sentenceRanges.last().let { if (it.isEmpty()) -1 else it.last }
            maxOf(sentenceRange.first, lastEnd + 1)
        } else {
            sentenceRange.first
        }
        val clampedRange = if (clampedStart > sentenceRange.last) {
            IntRange.EMPTY
        } else {
            clampedStart..sentenceRange.last
        }
        sentenceRanges += clampedRange
        sentenceStartByChar.fill(
            sentence.chapterStartMs.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt(),
            clampedRange.first,
            clampedRange.last + 1
        )
        var unitCursor = resolvedSentence.canonicalStart
        sentence.units.forEach { unit ->
            val resolvedUnit = canonical.find(
                text = unit.text,
                from = unitCursor,
                until = resolvedSentence.canonicalEnd
            )
            if (resolvedUnit == null) {
                unitRanges += IntRange.EMPTY
            } else {
                val clampedUnitStart = maxOf(resolvedUnit.rawRange.first, clampedRange.first)
                val clampedUnitRange = if (clampedUnitStart > resolvedUnit.rawRange.last) {
                    IntRange.EMPTY
                } else {
                    clampedUnitStart..resolvedUnit.rawRange.last
                }
                unitRanges += clampedUnitRange
                if (!clampedUnitRange.isEmpty()) {
                    unitStartByChar.fill(
                        unit.startMs.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt(),
                        clampedUnitRange.first,
                        clampedUnitRange.last + 1
                    )
                }
                unitCursor = resolvedUnit.canonicalEnd
            }
        }
        sentenceCursor = resolvedSentence.canonicalEnd
    }

    return ReadAlongTextIndex(
        chapterHref = chapterHref,
        plainText = plainText,
        elementIdByChar = IntArray(plainLength),
        sentenceStartByChar = sentenceStartByChar,
        unitStartByChar = unitStartByChar,
        sentenceRanges = sentenceRanges,
        unitRanges = unitRanges
    )
}

private data class CanonicalText(
    val text: String,
    val rawOffsetByCanonicalIndex: IntArray
) {
    fun find(text: String, from: Int, until: Int = this.text.length): ResolvedTextRange? {
        val needle = canonicalize(text).text
        if (needle.isEmpty() || from !in 0..this.text.length) return null
        val found = this.text.indexOf(needle, from)
        if (found < 0 || found + needle.length > until) return null
        val rawStart = rawOffsetByCanonicalIndex[found]
        val rawEnd = rawOffsetByCanonicalIndex[found + needle.length - 1] + 1
        return ResolvedTextRange(found, found + needle.length, rawStart until rawEnd)
    }
}

private data class ResolvedTextRange(
    val canonicalStart: Int,
    val canonicalEnd: Int,
    val rawRange: IntRange
)

private fun canonicalize(text: String): CanonicalText {
    val normalized = StringBuilder(text.length)
    val rawOffsets = ArrayList<Int>(text.length)
    text.forEachIndexed { index, char ->
        if (!char.isWhitespace()) {
            normalized.append(char)
            rawOffsets += index
        }
    }
    return CanonicalText(normalized.toString(), rawOffsets.toIntArray())
}

/**
 * Some legacy alignment records omit a closing dialogue quote even though the
 * corresponding EPUB sentence retains it. Include only the directly-adjacent
 * matching closer after a terminal punctuation mark; never advance an arbitrary
 * sentence end by one character.
 */
private fun expandTrailingClosingQuote(plainText: String, alignmentQuote: String, resolvedRange: IntRange): IntRange {
    val expectedCloser = unmatchedClosingQuote(alignmentQuote) ?: return resolvedRange
    val terminal = plainText.getOrNull(resolvedRange.last) ?: return resolvedRange
    if (terminal !in SENTENCE_TERMINATORS) return resolvedRange

    var next = resolvedRange.last + 1
    while (plainText.getOrNull(next)?.isWhitespace() == true) next++
    if (plainText.getOrNull(next) != expectedCloser) return resolvedRange
    return resolvedRange.first until (next + 1)
}

private fun unmatchedClosingQuote(text: String): Char? {
    val stack = mutableListOf<Char>()
    text.forEach { char ->
        when {
            char in OPENING_TO_CLOSING_QUOTE -> stack += char
            stack.isNotEmpty() && char == OPENING_TO_CLOSING_QUOTE.getValue(stack.last()) -> {
                stack.removeAt(stack.lastIndex)
            }
        }
    }
    return stack.lastOrNull()?.let(OPENING_TO_CLOSING_QUOTE::get)
}

private val OPENING_TO_CLOSING_QUOTE = mapOf(
    '“' to '”',
    '‘' to '’',
    '「' to '」',
    '『' to '』',
    '（' to '）',
    '(' to ')'
)

private val SENTENCE_TERMINATORS = setOf('。', '！', '？', '!', '?', '…')
