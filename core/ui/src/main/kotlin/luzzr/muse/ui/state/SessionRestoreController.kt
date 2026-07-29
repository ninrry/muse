package luzzr.muse.ui.state

interface SessionRestoreController {
    suspend fun restoreIfNeeded()
}
