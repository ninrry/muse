package luzzr.muse.media

import luzzr.muse.domain.model.Song

interface PlaybackActionController {
    fun togglePlayPause()
    fun skipToNext()
    fun skipToPrevious()
    fun seekTo(position: Long)
    fun playSongAtIndex(list: List<Song>, index: Int)
    fun playShuffled(list: List<Song>)
}
