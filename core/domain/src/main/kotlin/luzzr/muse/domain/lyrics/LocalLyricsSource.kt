package luzzr.muse.domain.lyrics

import luzzr.muse.domain.model.LyricsResult
import luzzr.muse.domain.model.Song

/**
 * Finds a sidecar LRC file for a local song.
 *
 * Implementations must only return a result when both title and artist match.
 */
interface LocalLyricsSource {
    suspend fun find(song: Song): LyricsResult?
}
