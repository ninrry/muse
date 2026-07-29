package luzzr.muse.data.tag

import luzzr.muse.core.log.MuseLog
import java.io.File
import java.security.MessageDigest

/**
 * File hashing utility for fast verification.
 * Uses MD5 for compatibility and speed - sufficient for integrity checks.
 */
object FileHasher {
    private const val BUFFER_SIZE = 8192

    /**
     * Compute MD5 hash of a file.
     * For large files (>10MB), this is much faster than byte-by-byte comparison.
     */
    fun computeMD5(file: File): String? {
        if (!file.exists() || !file.isFile) {
            return null
        }
        return try {
            val digest = MessageDigest.getInstance("MD5")
            file.inputStream().use { fis ->
                val buffer = ByteArray(BUFFER_SIZE)
                var read: Int
                while (fis.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            MuseLog.e("FileHasher", "Failed to compute MD5 for ${file.name}", e)
            null
        }
    }

    /**
     * Quick size-based verification.
     * Returns true if file sizes match (fast check).
     */
    fun quickVerify(original: File, edited: File): Boolean {
        return original.exists() && edited.exists() &&
            original.length() == edited.length() &&
            original.length() > 0
    }
}
