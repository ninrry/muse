package luzzr.muse.media

import luzzr.muse.domain.model.Song

data class PlaybackState(
    val playlist: List<Song> = emptyList(),
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val repeatMode: PlaybackRepeatMode = PlaybackRepeatMode.ALL,
    val shuffleEnabled: Boolean = false
)

enum class PlaybackRepeatMode {
    OFF,
    ONE,
    ALL
}
