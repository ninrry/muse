package luzzr.muse.domain.model

enum class MediaUsageType {
    MUSIC,
    AUDIOBOOK
}

data class MusicUsageStats(
    val totalListenedMs: Long = 0L,
    val todayListenedMs: Long = 0L,
    val weekListenedMs: Long = 0L,
    val playCount: Long = 0L,
    val todayPlayCount: Long = 0L,
    val listenedSongCount: Long = 0L,
    val lastPlayedAt: Long = 0L
)

data class ReadAlongUsageStats(
    val totalReadMs: Long = 0L,
    val todayReadMs: Long = 0L,
    val weekReadMs: Long = 0L,
    val totalListenedMs: Long = 0L,
    val todayListenedMs: Long = 0L,
    val weekListenedMs: Long = 0L
)
