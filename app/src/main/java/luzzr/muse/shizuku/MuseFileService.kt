package luzzr.muse.shizuku

import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * Shizuku UserService implementation that runs as shell/root and can write to
 * paths that are normally blocked by Scoped Storage.
 */
class MuseFileService : IMuseFileService.Stub() {

    override fun copyFile(sourcePath: String?, targetPath: String?): Boolean {
        if (sourcePath.isNullOrBlank() || targetPath.isNullOrBlank()) return false
        return try {
            val source = File(sourcePath)
            if (!source.exists()) {
                Log.w(TAG, "copyFile: source does not exist: $sourcePath")
                return false
            }
            replaceTarget(targetPath) { source.inputStream() }.also { success ->
                Log.d(TAG, "copyFile: copied $sourcePath -> $targetPath success=$success")
            }
        } catch (e: IOException) {
            Log.e(TAG, "copyFile: failed to copy $sourcePath -> $targetPath", e)
            false
        } catch (e: SecurityException) {
            Log.e(TAG, "copyFile: permission denied $sourcePath -> $targetPath", e)
            false
        }
    }

    override fun copyFileDescriptor(source: ParcelFileDescriptor?, targetPath: String?): Boolean {
        if (source == null || targetPath.isNullOrBlank()) return false
        return try {
            replaceTarget(targetPath) {
                ParcelFileDescriptor.AutoCloseInputStream(source)
            }.also { success ->
                Log.d(TAG, "copyFileDescriptor: copied fd -> $targetPath success=$success")
            }
        } catch (e: IOException) {
            Log.e(TAG, "copyFileDescriptor: failed to copy fd -> $targetPath", e)
            false
        } catch (e: SecurityException) {
            Log.e(TAG, "copyFileDescriptor: permission denied fd -> $targetPath", e)
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

    override fun renameFile(sourcePath: String?, targetPath: String?): Boolean {
        if (sourcePath.isNullOrBlank() || targetPath.isNullOrBlank()) return false
        return try {
            val source = File(sourcePath)
            val target = File(targetPath)
            if (!source.exists()) {
                Log.w(TAG, "renameFile: source does not exist: $sourcePath")
                return false
            }
            if (target.exists()) {
                Log.w(TAG, "renameFile: target already exists: $targetPath")
                return false
            }
            target.parentFile?.mkdirs()
            val renamed = source.renameTo(target)
            if (renamed) {
                target.setReadable(true, false)
                target.setWritable(true, false)
            }
            Log.d(TAG, "renameFile: $sourcePath -> $targetPath renamed=$renamed")
            renamed
        } catch (e: SecurityException) {
            Log.e(TAG, "renameFile: permission denied $sourcePath -> $targetPath", e)
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

    private fun replaceTarget(targetPath: String, openSource: () -> InputStream): Boolean {
        val target = File(targetPath)
        val parent = target.parentFile ?: return false
        if (!parent.exists() && !parent.mkdirs()) {
            Log.w(TAG, "replaceTarget: unable to create parent: ${parent.absolutePath}")
            return false
        }

        val token = "${System.currentTimeMillis()}_${System.nanoTime()}"
        val temp = File(parent, ".${target.name}.muse-$token.tmp")
        val backup = File(parent, ".${target.name}.muse-$token.bak")
        var backupCreated = false
        val startedAt = System.currentTimeMillis()

        return try {
            Log.w(TAG, "replaceTarget: start target=$targetPath")
            openSource().use { input ->
                temp.outputStream().use { output -> input.copyTo(output, COPY_BUFFER_SIZE) }
            }
            if (!temp.isFile || temp.length() <= 0L) {
                Log.w(TAG, "replaceTarget: temp file is empty: ${temp.absolutePath}")
                return false
            }

            if (target.exists()) {
                if (!target.renameTo(backup)) {
                    Log.w(TAG, "replaceTarget: unable to move target to backup: ${target.absolutePath}")
                    return false
                }
                backupCreated = true
            }

            if (!temp.renameTo(target)) {
                Log.w(TAG, "replaceTarget: unable to move temp into place: ${target.absolutePath}")
                restoreBackup(target, backup, backupCreated)
                return false
            }

            target.setReadable(true, false)
            target.setWritable(true, false)
            backup.delete()
            Log.w(
                TAG,
                "replaceTarget: complete target=$targetPath bytes=${target.length()} ms=${System.currentTimeMillis() - startedAt}"
            )
            true
        } catch (e: IOException) {
            Log.e(TAG, "replaceTarget: IO failure for $targetPath", e)
            restoreBackup(target, backup, backupCreated)
            false
        } catch (e: SecurityException) {
            Log.e(TAG, "replaceTarget: permission denied for $targetPath", e)
            restoreBackup(target, backup, backupCreated)
            false
        } finally {
            temp.delete()
        }
    }

    private fun restoreBackup(target: File, backup: File, backupCreated: Boolean) {
        if (!backupCreated) return
        try {
            if (target.exists()) target.delete()
            backup.renameTo(target)
        } catch (e: SecurityException) {
            Log.e(TAG, "restoreBackup: failed for ${target.absolutePath}", e)
        }
    }

    private companion object {
        const val TAG = "MuseFileService"
        const val COPY_BUFFER_SIZE = 256 * 1024
    }
}
