package luzzr.muse.domain.lyrics

import luzzr.muse.domain.model.LyricsResult
import luzzr.muse.domain.model.Song

/**
 * Finds a sidecar LRC file for a local song.
 *
 * An exact audio/LRC filename match is authoritative. Other candidates must
 * match both title and artist.
 */
interface LocalLyricsSource {
    suspend fun find(song: Song): LyricsResult?
}
