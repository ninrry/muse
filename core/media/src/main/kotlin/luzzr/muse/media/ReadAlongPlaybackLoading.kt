package luzzr.muse.media

/**
 * A stopped Media3 player can retain its media item while returning to IDLE.
 * URI equality alone is therefore not a valid signal that the source is ready
 * to play again.
 */
internal fun shouldPrepareReadAlongMedia(
    currentUri: String?,
    requestedUri: String,
    isIdle: Boolean
): Boolean = currentUri != requestedUri || isIdle
