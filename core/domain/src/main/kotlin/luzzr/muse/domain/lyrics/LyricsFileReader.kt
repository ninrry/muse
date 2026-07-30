package luzzr.muse.domain.lyrics

/**
 * Reads a user-selected lyrics document.
 *
 * The URI stays outside the domain model as an opaque string so Android storage
 * access remains an implementation detail.
 */
interface LyricsFileReader {
    suspend fun read(uri: String): LyricsFileReadResult
}

sealed interface LyricsFileReadResult {
    data class Success(val content: String) : LyricsFileReadResult
    data object TooLarge : LyricsFileReadResult
    data object Unreadable : LyricsFileReadResult
}
