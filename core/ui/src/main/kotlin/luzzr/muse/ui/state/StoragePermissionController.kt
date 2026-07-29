package luzzr.muse.ui.state

interface StoragePermissionController {
    fun hasFullFileAccess(): Boolean
    fun requestFullFileAccess()
}
