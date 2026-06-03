package luzzr.muse.domain.model

enum class SortType(val label: String) {
    TITLE_ASC("标题 A→Z"),
    TITLE_DESC("标题 Z→A"),
    ARTIST_ASC("艺术家 A→Z"),
    ARTIST_DESC("艺术家 Z→A"),
    ALBUM_ASC("专辑 A→Z"),
    ALBUM_DESC("专辑 Z→A"),
    DURATION_ASC("时长 ↑"),
    DURATION_DESC("时长 ↓"),
    DATE_ADDED_DESC("最近添加"),
    DATE_ADDED_ASC("最早添加")
}
