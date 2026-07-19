package luzzr.muse.ui.screens.settings

/** A permission status that can be rendered without losing the reason why it exists. */
enum class PermissionStatus {
    GRANTED,
    MISSING,
    NOT_REQUIRED
}

data class PermissionSnapshot(
    val audio: PermissionStatus,
    val fullFileAccess: PermissionStatus,
    val notifications: PermissionStatus
) {
    val allGranted: Boolean
        get() = audio != PermissionStatus.MISSING &&
            fullFileAccess != PermissionStatus.MISSING &&
            notifications != PermissionStatus.MISSING
}
