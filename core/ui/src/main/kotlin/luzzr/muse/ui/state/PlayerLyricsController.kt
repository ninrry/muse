package luzzr.muse.ui.state

import luzzr.muse.domain.model.LrcLine
import luzzr.muse.domain.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

interface PlayerLyricsController {
    val lyrics: StateFlow<List<LrcLine>>
    val currentLyricLine: StateFlow<Int>
    val lineProgress: StateFlow<Float>
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
