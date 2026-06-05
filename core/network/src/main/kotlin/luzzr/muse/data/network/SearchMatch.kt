package luzzr.muse.data.network

import java.util.Locale
import kotlin.math.roundToInt

/**
 * Shared matching helpers for metadata and lyrics lookups.
 *
 * The network sources all return ranked results, but their ranking is often based on
 * global popularity rather than this local file's title/artist. Keep a local score
 * gate so common titles do not silently attach the wrong metadata or lyrics.
 */
internal object SearchMatch {
    private val noiseRegex = Regex(
        "【[^】]*】|\\[[^\\]]*]|\\([^)]*\\)|" +
            "(?i)official|music\\s*video|lyrics?|lyric\\s*video|mv|live|audio|" +
            "visualizer|remaster(?:ed)?|version|完整版|纯享|伴奏|高清|超清|无损|高音质|4k|hd"
    )

    private val normalizedAliasesMap: Map<String, Set<String>> = mapOf(
        "周杰伦" to setOf("jay chou", "jaychou", "zhou jielun", "zhoujielun"),
        "周杰倫" to setOf("jay chou", "jaychou", "zhou jielun", "zhoujielun"),
        "陈奕迅" to setOf("eason chan", "easonchan", "chen yixun", "chenyixun"),
        "陳奕迅" to setOf("eason chan", "easonchan", "chen yixun", "chenyixun"),
        "王菲" to setOf("faye wong", "fayewong", "wang fei", "wangfei"),
        "张学友" to setOf("jacky cheung", "jackycheung", "zhang xueyou", "zhangxueyou"),
        "張學友" to setOf("jacky cheung", "jackycheung", "zhang xueyou", "zhangxueyou"),
        "刘德华" to setOf("andy lau", "andylau", "liu dehua", "liudehua"),
        "劉德華" to setOf("andy lau", "andylau", "liu dehua", "liudehua"),
        "林俊杰" to setOf("jj lin", "jjlin", "lin junjie", "linjunjie"),
        "林俊傑" to setOf("jj lin", "jjlin", "lin junjie", "linjunjie"),
        "邓紫棋" to setOf("gem", "g.e.m.", "deng ziqi", "dengziqi"),
        "鄧紫棋" to setOf("gem", "g.e.m.", "deng ziqi", "dengziqi"),
        "陶喆" to setOf("david tao", "davidtao", "tao zhe", "taozhe"),
        "王力宏" to setOf("leehom wang", "leehomwang", "wang lihong", "wanglihong"),
        "蔡依林" to setOf("jolin tsai", "jolintsai", "cai yilin", "caiyilin"),
        "张惠妹" to setOf("a mei", "amei", "a-mei", "zhang huimei", "zhanghuimei"),
        "張惠妹" to setOf("a mei", "amei", "a-mei", "zhang huimei", "zhanghuimei"),
        "萧敬腾" to setOf("jam hsiao", "jamhsiao", "xiao jingteng", "xiaojingteng"),
        "蕭敬騰" to setOf("jam hsiao", "jamhsiao", "xiao jingteng", "xiaojingteng"),
        "李荣浩" to setOf("li ronghao", "lironghao"),
        "李營浩" to setOf("li ronghao", "lironghao"),
        "李榮浩" to setOf("li ronghao", "lironghao"),
        "薛之谦" to setOf("joker xue", "jokerxue", "xue zhiqian", "xuezhiqian"),
        "薛之謙" to setOf("joker xue", "jokerxue", "xue zhiqian", "xuezhiqian"),
        "张杰" to setOf("jason zhang", "jasonzhang", "zhang jie", "zhangjie"),
        "張傑" to setOf("jason zhang", "jasonzhang", "zhang jie", "zhangjie"),
        "华晨宇" to setOf("chenyu hua", "chenyuhua", "hua chenyu", "huachenyu"),
        "華晨宇" to setOf("chenyu hua", "chenyuhua", "hua chenyu", "huachenyu"),
        "许嵩" to setOf("vae", "xu song", "xusong"),
        "許嵩" to setOf("vae", "xu song", "xusong"),
        "汪苏泷" to setOf("silence wang", "silencewang", "wang sulong", "wangsulong"),
        "汪蘇瀧" to setOf("silence wang", "silencewang", "wang sulong", "wangsulong"),
        "梁静茹" to setOf("fish leong", "fishleong", "liang jingru", "liangjingru"),
        "梁靜茹" to setOf("fish leong", "fishleong", "liang jingru", "liangjingru"),
        "孙燕姿" to setOf("stefanie sun", "stefaniesun", "sun yanzi", "sunyanzi"),
        "孫燕姿" to setOf("stefanie sun", "stefaniesun", "sun yanzi", "sunyanzi"),
        "莫文蔚" to setOf("karen mok", "karenmok", "mo wenwei", "mowenwei"),
        "张韶涵" to setOf("angela chang", "angelachang", "zhang shaohan", "zhangshaohan"),
        "張韶涵" to setOf("angela chang", "angelachang", "zhang shaohan", "zhangshaohan"),
        "杨丞琳" to setOf("rainie yang", "rainieyang", "yang chenglin", "yangchenglin"),
        "楊丞琳" to setOf("rainie yang", "rainieyang", "yang chenglin", "yangchenglin"),
        "王心凌" to setOf("cyndi wang", "cyndiwang", "wang xinling", "wangxinling"),
        "伍佰" to setOf("wu bai", "wubai"),
        "苏打绿" to setOf("sodagreen", "soda green"),
        "蘇打綠" to setOf("sodagreen", "soda green"),
        "五月天" to setOf("mayday"),
        "告五人" to setOf("accusefive", "accuse five")
    ).entries.flatMap { entry ->
        val normKey = normalize(entry.key)
        val normValues = entry.value.map { normalize(it) }.filter { it.isNotBlank() }.toSet()
        val allNames = normValues + normKey
        allNames.map { name -> name to (allNames - name) }
    }.groupBy({ it.first }, { it.second })
        .mapValues { (_, values) -> values.flatten().toSet() }

    private val unknownMarkers = setOf(
        "",
        "unknown",
        "unknownartist",
        "unknownalbum",
        "unknownsong",
        "unknowntrack",
        "unknownsinger",
        "<unknown>",
        "未知",
        "未知艺术家",
        "未知歌手",
        "未知专辑",
        "未知歌曲"
    )

    fun extractBookTitle(title: String): String {
        val regex = Regex("《([^》]+)》")
        val matchResult = regex.find(title)
        return matchResult?.groupValues?.get(1)?.trim() ?: title
    }

    fun cleanOptional(value: String?): String? {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isBlank()) return null
        return trimmed.takeIf { normalize(it) !in unknownMarkers }
    }

    fun normalize(value: String?): String {
        if (value.isNullOrBlank()) return ""
        return toSimplifiedText(value)
            .lowercase(Locale.ROOT)
            .replace(noiseRegex, " ")
            .filter { it.isLetterOrDigit() }
    }

    fun titleScore(query: String, candidate: String): Int {
        val q = normalize(query)
        val c = normalize(candidate)
        if (q.isBlank() || c.isBlank()) return 0
        if (q == c) return 60
        if (c.contains(q)) return 54
        return titleContainedOrOverlapScore(q, c)
    }

    private fun titleContainedOrOverlapScore(q: String, c: String): Int {
        if (q.contains(c) && c.length >= (q.length / 2).coerceAtLeast(2)) return 42
        return overlapScore(q, c, 36)
    }

    fun artistScore(query: String?, candidate: String?): Int {
        val cleanQuery = cleanOptional(query) ?: return 24
        val q = normalize(cleanQuery)
        val c = normalize(candidate)
        return when {
            q.isBlank() || c.isBlank() -> 0
            q == c -> 32
            normalizedAliasesMap[q]?.contains(c) == true -> 32
            c.contains(q) || q.contains(c) -> 26
            else -> overlapScore(q, c, 20)
        }
    }

    fun trackScore(queryTitle: String, queryArtist: String?, candidateTitle: String, candidateArtist: String?): Int {
        val title = titleScore(queryTitle, candidateTitle)
        if (title < 18) return title
        return (title + artistScore(queryArtist, candidateArtist)).coerceIn(0, 100)
    }

    fun minimumAcceptableScore(queryArtist: String?): Int {
        return if (cleanOptional(queryArtist) == null) 34 else 46
    }

    private fun overlapScore(query: String, candidate: String, maxScore: Int): Int {
        val queryChars = query.toSet()
        if (queryChars.isEmpty()) return 0
        val overlap = queryChars.count { it in candidate }.toDouble() / queryChars.size
        return (overlap * maxScore).roundToInt()
    }

    fun canonicalizeArtist(artist: String?): String {
        val clean = cleanOptional(artist) ?: return ""
        val norm = normalize(clean)
        val aliases = normalizedAliasesMap[norm]
        if (aliases.isNullOrEmpty()) return norm
        return (aliases + norm).sorted().first()
    }
}
