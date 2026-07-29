package luzzr.muse.media

import kotlinx.coroutines.flow.StateFlow
import luzzr.muse.domain.model.ReadAlongUnit

/**
 * Single surface the reader uses to consume imported chapter audio and its
 * alignment timeline. There is intentionally no speech-synthesis implementation.
 */
interface ReadAlongAudioSource {
    val state: StateFlow<ReadAlongPlaybackState>
    /**
     * Load audio for a sentence. [audioFile] is the absolute path of the chapter audio
     * (resolved by the repository; not a hard-coded path). [bookId] is propagated so the
     * service notification can reflect the active book.
     */
    fun load(
        bookId: String,
        chapterId: String,
        units: List<ReadAlongUnit>,
        sentenceStartMs: Long,
        sentenceEndMs: Long,
        audioFile: String?,
        initialPositionMs: Long,
        autoPlay: Boolean
    )
    fun seekTo(positionMs: Long)
    fun setRate(rate: Float)
    fun togglePlay()
    fun stop()
    /** Project a current playback position to a unit index. */
    fun unitIndexAt(positionMs: Long, units: List<ReadAlongUnit>): Int
}
