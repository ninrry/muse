package luzzr.muse.domain.model

/**
 * Domain error types for lyrics operations.
 *
 * These are domain-level errors that can be observed by any layer.
 * The UI layer can then map these to appropriate display types (UiText, etc.)
 */
sealed class LyricsError {
    /** Lyrics were not found for this song */
    object NotFound : LyricsError()

    /** Only plain text lyrics available, no synced lyrics */
    object PlainOnly : LyricsError()

    /** Network error while fetching lyrics */
    object NetworkError : LyricsError()

    /** Unknown error occurred */
    data class Unknown(val message: String?) : LyricsError()
}
