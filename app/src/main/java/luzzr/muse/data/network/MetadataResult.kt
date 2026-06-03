package luzzr.muse.data.network

import luzzr.muse.core.log.MuseLog

typealias MetadataResult = luzzr.muse.domain.model.MetadataResult

fun MetadataResult.toSimplifiedChinese(): MetadataResult {
    if (!hasTraditionalChinese(title, artist, album)) return this
    return copy(
        title = toSimplifiedText(title),
        artist = toSimplifiedText(artist),
        album = toSimplifiedText(album),
        genre = toSimplifiedText(genre)
    )
}

private val ICU_TRANSLITERATOR: ((String) -> String)? = try {
    val instance = android.icu.text.Transliterator.getInstance("Traditional-Simplified")
    val func: (String) -> String = { s -> instance.transliterate(s) }
    func
} catch (e: Exception) {
    MuseLog.w("MetadataResult", "ICU transliterator unavailable", e)
    null
}

private val MANUAL_OVERRIDES = mapOf(
    '\u5973' to '\u4F60',
    '\u7262' to '\u5B83',
    '\u8846' to '\u53EA',
    '\u8457' to '\u7740',
    '\u88CF' to '\u91CC',
    '\u9B31' to '\u90C1',
    '\u5118' to '\u5C3D'
)

fun toSimplifiedText(text: String): String {
    val icuResult = try {
        ICU_TRANSLITERATOR?.invoke(text) ?: text
    } catch (e: Exception) {
        MuseLog.w("MetadataResult", "ICU transliterate failed for: ${text.take(40)}", e)
        text
    }
    return icuResult.map { c -> MANUAL_OVERRIDES[c] ?: c }.joinToString("")
}

private fun hasTraditionalChinese(vararg texts: String): Boolean {
    for (text in texts) {
        if (text.any { it.code in 0x4E00..0x9FFF }) return true
    }
    return false
}
