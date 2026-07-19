package luzzr.muse.media

import luzzr.muse.domain.model.LrcLine
import luzzr.muse.domain.model.Song
import kotlinx.coroutines.flow.StateFlow

interface PlaybackController {
    val state: StateFlow<PlaybackState>
    val sleepTimer: SleepTimerController

    fun playSongs(songs: List<Song>, startIndex: Int = 0, enableShuffle: Boolean = false)
    fun playShuffled(songs: List<Song>)
    fun togglePlayPause()
    fun seekTo(positionMs: Long)
    fun skipToNext()
    fun skipToPrevious()
    fun setRepeatMode(mode: PlaybackRepeatMode)
    fun toggleShuffle()
    fun regenerateShuffleQueueAndPlay()
    fun refreshCurrentSong(song: Song)
    fun publishLyrics(lyrics: List<LrcLine>)
    fun publishCurrentLyricLine(line: Int)
    fun publishLyricsOffset(offsetMs: Long) {}

    fun hasSavedSession(): Boolean
    fun getSavedPlaylistIds(): List<Long>
    fun getSavedPlaybackInfo(): Pair<Int, Long>
    fun getSavedShuffleMode(): Boolean
    fun getSavedRepeatMode(): PlaybackRepeatMode
    fun getSavedSongProgress(songId: Long): Long
    fun getSongLastPlayedTime(songId: Long): Long
    fun clearSavedSession()
}
