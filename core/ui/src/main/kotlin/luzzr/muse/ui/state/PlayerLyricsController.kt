package luzzr.muse.ui.state

import luzzr.muse.domain.model.LrcLine
import luzzr.muse.domain.model.LyricsResult
import luzzr.muse.domain.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

interface PlayerLyricsController {
    val lyrics: StateFlow<List<LrcLine>>
    val currentLyricLine: StateFlow<Int>
    val positionMs: StateFlow<Long>
    val lyricsLoading: StateFlow<Boolean>
    val lyricsError: StateFlow<UiText?>
    val lyricsOffsetMs: StateFlow<Long>

    fun bind(scope: CoroutineScope, progressFlow: StateFlow<Long>)
    suspend fun loadLyrics(song: Song)
    fun resetLyrics(scope: CoroutineScope, song: Song)
    fun adjustLyricsOffset(scope: CoroutineScope, songId: Long, deltaMs: Long)
    fun saveLyricsOffset(scope: CoroutineScope, songId: Long, offsetMs: Long, bakeToFile: Boolean = false)
    fun resetLyricsOffset(scope: CoroutineScope, songId: Long)
    fun commitLyricsOffset(scope: CoroutineScope, song: Song)
    suspend fun searchLyricsCandidates(song: Song): List<LyricsResult>
    suspend fun applyLyricsResult(song: Song, result: LyricsResult)
    fun clear()
}
