package luzzr.muse.data.repository

import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * Writes artwork without exposing a truncated destination file to image loaders.
 *
 * The content digest in the URI fragment does not change the file path, but it does
 * give Coil and StateFlow a new identity when the artwork bytes change.
 */
internal object ArtworkCacheStorage {

    @Throws(IOException::class)
    fun write(file: File, bytes: ByteArray): String {
        val parent = file.parentFile
        if (parent == null || (!parent.exists() && !parent.mkdirs())) {
            throw IOException("Unable to create artwork cache directory for ${file.absolutePath}")
        }

        val temporaryFile = File.createTempFile("${file.name}.", ".tmp", parent)
        try {
            FileOutputStream(temporaryFile).use { stream ->
                stream.write(bytes)
                stream.fd.sync()
            }
            try {
                Files.move(
                    temporaryFile.toPath(),
                    file.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    temporaryFile.toPath(),
                    file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }
        } finally {
            if (temporaryFile.exists()) {
                temporaryFile.delete()
            }
        }

        val digest = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .take(12)
            .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
        return Uri.fromFile(file)
            .buildUpon()
            .fragment("sha256=$digest")
            .build()
            .toString()
    }

    fun delete(file: File) {
        if (file.exists()) {
            file.delete()
        }
    }
}
