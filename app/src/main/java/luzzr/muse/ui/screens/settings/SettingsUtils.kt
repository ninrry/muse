package luzzr.muse.ui.screens.settings

import android.net.Uri

internal fun safTreeUriToPath(uri: Uri): String? {
    return try {
        val docId = java.net.URLDecoder.decode(uri.lastPathSegment ?: return null, "UTF-8")
        val parts = docId.split(":")
        val storage = if (parts[0] == "primary") "emulated/0" else parts[0]
        val folderPath = if (parts.size > 1) "/${parts[1]}" else ""
        "/storage/$storage$folderPath"
    } catch (_: Exception) {
        null
    }
}
