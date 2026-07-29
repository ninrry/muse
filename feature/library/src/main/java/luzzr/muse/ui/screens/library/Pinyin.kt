package luzzr.muse.ui.screens.library

import net.sourceforge.pinyin4j.PinyinHelper
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType

private val pinyinFormat = HanyuPinyinOutputFormat().apply {
    toneType = HanyuPinyinToneType.WITHOUT_TONE
}

/**
 * 拼音首字母工具（基于 pinyin4j，逐字准确）。
 */
object Pinyin {

    /**
     * 返回字符的“字母键”：
     * - 英文字母 -> 自身（大写）
     * - 数字 -> 自身
     * - 中文 -> 拼音首字母（大写）
     * - 其他符号 / 未收录字符 -> '#'
     */
    fun initial(ch: Char): Char {
        // 仅 ASCII 英文字母走快速路径；中文 isLetter() 也为 true，必须落到 pinyin4j
        if (ch in 'a'..'z' || ch in 'A'..'Z') return ch.uppercaseChar()
        if (ch.isDigit()) return ch
        val arr = PinyinHelper.toHanyuPinyinStringArray(ch, pinyinFormat)
        if (!arr.isNullOrEmpty()) {
            return arr[0].first().uppercaseChar()
        }
        return '#'
    }

    /**
     * 生成排序 / 分组键：逐字符取首字母并大写拼接。
     * 纯英文 / 数字保持原样；中文转为拼音首字母串；符号 / 未收录为 '#'。
     */
    fun sortKey(text: String): String {
        if (text.isEmpty()) return "#"
        val sb = StringBuilder(text.length)
        for (c in text) sb.append(initial(c))
        return sb.toString()
    }
}
