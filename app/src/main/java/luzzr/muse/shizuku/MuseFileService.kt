package luzzr.muse.shizuku

import android.util.Log
import java.io.File
import java.io.IOException

/**
 * Shizuku UserService implementation that runs as shell/root and can write to
 * paths that are normally blocked by Scoped Storage.
 */
class MuseFileService : IMuseFileService.Stub() {

    override fun copyFile(sourcePath: String?, targetPath: String?): Boolean {
        if (sourcePath.isNullOrBlank() || targetPath.isNullOrBlank()) return false
        return try {
            val source = File(sourcePath)
            val target = File(targetPath)
            if (!source.exists()) {
                Log.w(TAG, "copyFile: source does not exist: $sourcePath")
                return false
            }
            target.parentFile?.mkdirs()
            source.inputStream().use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            // Ensure the file is readable/writable by the owning user.
            target.setReadable(true, false)
            target.setWritable(true, false)
            Log.d(TAG, "copyFile: copied $sourcePath -> $targetPath")
            true
        } catch (e: IOException) {
            Log.e(TAG, "copyFile: failed to copy $sourcePath -> $targetPath", e)
            false
        } catch (e: SecurityException) {
            Log.e(TAG, "copyFile: permission denied $sourcePath -> $targetPath", e)
            false
        }
    }

    override fun deleteFile(path: String?): Boolean {
        if (path.isNullOrBlank()) return false
        return try {
            File(path).delete().also { deleted ->
                Log.d(TAG, "deleteFile: $path deleted=$deleted")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "deleteFile: permission denied $path", e)
            false
        }
    }

    override fun fileSize(path: String?): Long {
        if (path.isNullOrBlank()) return -1L
        return try {
            File(path).length()
        } catch (e: SecurityException) {
            Log.e(TAG, "fileSize: permission denied $path", e)
            -1L
        }
    }

    private companion object {
        const val TAG = "MuseFileService"
    }
}
