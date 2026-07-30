package luzzr.muse.data.scanner

import android.content.Context
import android.media.MediaScannerConnection
import dagger.hilt.android.qualifiers.ApplicationContext
import luzzr.muse.core.log.MuseLog
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Refreshes only the files that were changed and waits for MediaStore to finish.
 *
 * MediaScannerConnection.scanFile is otherwise fire-and-forget. Callers could update
 * the in-app model before MediaStore had noticed a rename, leaving a stale content URI
 * until the next full library scan.
 */
@Singleton
class MediaStoreFileRefresher @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun refresh(vararg rawPaths: String): Map<String, String> {
        val paths = rawPaths.filter(String::isNotBlank).distinct()
        if (paths.isEmpty()) return emptyMap()

        val lock = Any()
        val scannedUris = linkedMapOf<String, String>()
        var completedCallbacks = 0
        val completed = withTimeoutOrNull(MEDIA_SCAN_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                try {
                    MediaScannerConnection.scanFile(
                        context,
                        paths.toTypedArray(),
                        null
                    ) { scannedPath, scannedUri ->
                        synchronized(lock) {
                            if (!scannedPath.isNullOrBlank() && scannedUri != null) {
                                scannedUris[scannedPath] = scannedUri.toString()
                            }
                            completedCallbacks++
                            if (completedCallbacks >= paths.size && continuation.isActive) {
                                continuation.resume(Unit)
                            }
                        }
                    }
                } catch (e: Exception) {
                    MuseLog.e("MediaStoreFileRefresher", "Targeted MediaStore refresh failed", e)
                    if (continuation.isActive) continuation.resume(Unit)
                }
            }
            true
        } ?: false

        if (!completed) {
            MuseLog.w(
                "MediaStoreFileRefresher",
                "Targeted MediaStore refresh timed out for ${paths.size} file(s)"
            )
        }
        return synchronized(lock) { scannedUris.toMap() }
    }

    private companion object {
        const val MEDIA_SCAN_TIMEOUT_MS = 8_000L
    }
}
