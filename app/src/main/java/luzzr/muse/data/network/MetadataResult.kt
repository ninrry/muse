package luzzr.muse.data.network

/**
 * Result from network metadata lookup (MusicBrainz / Deezer).
 */
data class MetadataResult(
    val title: String,
    val artist: String,
    val album: String = "",
    val year: Int? = null,
    val genre: String = "",
    val coverUrl: String? = null,
    val source: String = "MusicBrainz",
    val score: Int = 0   // confidence 0-100
) {
    /** Returns a copy with all Chinese text fields converted to Simplified Chinese. */
    fun toSimplifiedChinese(): MetadataResult {
        val converter = Companion::toSimplifiedText
        if (!hasTraditionalChinese(title, artist, album)) return this
        return copy(
            title = converter(title),
            artist = converter(artist),
            album = converter(album),
            genre = converter(genre)
        )
    }

    companion object {
        /** ICU Traditional→Simplified transliterator. */
        private val ICU_TRANSLITERATOR: (String) -> String =
            { s: String -> android.icu.text.Transliterator.getInstance("Traditional-Simplified").transliterate(s) }

        /** Manual override mapping for characters ICU may miss on some platforms. */
        private val MANUAL_OVERRIDES = mapOf(
            '妳' to '你',  // female "you" → generic "you"
            '牠' to '它',  // animal "it" → "it"
            '衹' to '只',
            '著' to '着',
            '裏' to '里',
            '鬱' to '郁',
            '儘' to '尽',
        )

        /** Convert Traditional Chinese text to Simplified Chinese. Public API for reuse. */
        fun toSimplifiedText(text: String): String {
            val icuResult = ICU_TRANSLITERATOR(text)
            // Apply manual overrides for characters ICU may have missed
            return icuResult.map { c -> MANUAL_OVERRIDES[c] ?: c }.joinToString("")
        }

        private fun hasTraditionalChinese(vararg texts: String): Boolean {
            for (text in texts) {
                if (text.any { it.code in 0x4E00..0x9FFF }) return true
            }
            return false
        }
    }
}
