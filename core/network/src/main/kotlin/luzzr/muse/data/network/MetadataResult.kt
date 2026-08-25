package luzzr.muse.data.network

import android.os.Build
import androidx.annotation.RequiresApi
import luzzr.muse.core.log.MuseLog
import luzzr.muse.domain.model.MetadataResult
import luzzr.muse.domain.text.TextNormalizer

fun MetadataResult.toSimplifiedChinese(): MetadataResult {
    if (!hasTraditionalChinese(title, artist, album)) return this
    return copy(
        title = toSimplifiedText(title),
        artist = toSimplifiedText(artist),
        album = toSimplifiedText(album),
        genre = toSimplifiedText(genre)
    )
}

private val ICU_TRANSLITERATOR: ((String) -> String)? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        createIcuTransliterator()
    } else {
        null
    }

@RequiresApi(Build.VERSION_CODES.Q)
private fun createIcuTransliterator(): ((String) -> String)? = try {
    val instance = android.icu.text.Transliterator.getInstance("Traditional-Simplified")
    val transliterate: (String) -> String = { text -> instance.transliterate(text) }
    transliterate
} catch (e: Exception) {
    MuseLog.w("MetadataResult", "ICU transliterator unavailable", e)
    null
}

private val MANUAL_OVERRIDES = mapOf(
    '妳' to '你',
    '牠' to '它',
    '隻' to '只',
    '衆' to '众',
    '著' to '着',
    '裏' to '里',
    '鬱' to '郁',
    '儘' to '尽'
)

class AndroidTextNormalizer : TextNormalizer {
    override fun toSimplified(text: String): String {
        val icuResult = try {
            ICU_TRANSLITERATOR?.invoke(text) ?: text
        } catch (e: Exception) {
            MuseLog.w("MetadataResult", "ICU transliterate failed for: ${text.take(40)}", e)
            text
        }
        return icuResult.map { c -> MANUAL_OVERRIDES[c] ?: c }.joinToString("")
    }
}

private val DEFAULT_TEXT_NORMALIZER = AndroidTextNormalizer()

fun toSimplifiedText(text: String): String = DEFAULT_TEXT_NORMALIZER.toSimplified(text)

private fun hasTraditionalChinese(vararg texts: String): Boolean {
    for (text in texts) {
        if (text.any { it.code in 0x4E00..0x9FFF }) return true
    }
    return false
}
