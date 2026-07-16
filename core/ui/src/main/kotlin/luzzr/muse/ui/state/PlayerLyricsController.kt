package luzzr.muse.ui.state

import luzzr.muse.domain.model.LrcLine
import luzzr.muse.domain.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

interface PlayerLyricsController {
    val lyrics: StateFlow<List<LrcLine>>
    val currentLyricLine: StateFlow<Int>
    /**
     * Current playback position in ms. Updated 20Hz by [bind].
     * UI is expected to read this in a per-frame coroutine (withFrameNanos)
     * to compute per-line progress, rather than relying on a pre-computed
     * Float. This keeps karaoke fill rendering at the display refresh rate
     * (60-120Hz) without forcing 20Hz StateFlow writes for line progress.
     */
    val positionMs: StateFlow<Long>
    val lyricsLoading: StateFlow<Boolean>
    val lyricsError: StateFlow<UiText?>
    val lyricsOffsetMs: StateFlow<Long>

    fun bind(scope: CoroutineScope, progressFlow: StateFlow<Long>)
    suspend fun loadLyrics(song: Song)
    fun resetLyrics(scope: CoroutineScope, song: Song)
    fun adjustLyricsOffset(scope: CoroutineScope, songId: Long, deltaMs: Long)
    fun saveLyricsOffset(scope: CoroutineScope, songId: Long, offsetMs: Long)
    fun resetLyricsOffset(scope: CoroutineScope, songId: Long)
    fun clear()
}
