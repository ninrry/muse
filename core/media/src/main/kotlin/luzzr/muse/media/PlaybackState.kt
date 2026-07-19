package luzzr.muse.media

import luzzr.muse.domain.model.LrcLine
import luzzr.muse.domain.model.Song

data class PlaybackState(
    val playlist: List<Song> = emptyList(),
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val repeatMode: PlaybackRepeatMode = PlaybackRepeatMode.ALL,
    val shuffleEnabled: Boolean = false,
    val floatingLyricsEnabled: Boolean = false,
    val lyrics: List<LrcLine> = emptyList(),
    val currentLyricLine: Int = -1,
    val lyricsOffsetMs: Long = 0L
)

enum class PlaybackRepeatMode {
    OFF,
    ONE,
    ALL
}
