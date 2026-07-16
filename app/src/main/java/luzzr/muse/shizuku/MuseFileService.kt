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

    // 内置存储与常见媒体卷；禁止裸 /storage（会匹配所有外置卷任意路径）
    private val allowedBaseDirs: List<File> by lazy {
        listOf(
            File("/storage/emulated/0"),
            File("/storage/emulated/0/Music"),
            File("/storage/emulated/0/Download"),
            File("/storage/emulated/0/Downloads"),
            File("/storage/emulated/0/Podcasts"),
            File("/storage/emulated/0/Audiobooks"),
            File("/storage/emulated/0/Android/media"),
        ).filter { it.exists() }
    }

    private val allowedAudioExt = setOf(
        "mp3", "flac", "m4a", "aac", "ogg", "opus", "wav", "wma", "aiff", "alac", "ape", "dsf", "dff"
    )

    /**
     * Validates that the path is safe and within allowed directories.
     * Prevents path traversal attacks (e.g., "../../etc/passwd")
     */
    private fun isPathAllowed(path: String): Boolean {
        if (path.isBlank()) return false
        try {
            if (path.contains("..")) {
                Log.w(TAG, "Path validation failed: traversal")
                return false
            }
            val file = File(path)
            val normalizedPath = file.canonicalPath
            val ext = file.extension.lowercase()
            // 写入目标应为音频扩展名（目录操作除外）
            if (ext.isNotEmpty() && ext !in allowedAudioExt && !file.isDirectory) {
                // 允许 .tmp / .bak 中间文件
                if (!path.contains(".muse-") && !path.endsWith(".tmp") && !path.endsWith(".bak")) {
                    Log.w(TAG, "Path validation failed: non-audio ext")
                    return false
                }
            }
            for (baseDir in allowedBaseDirs) {
                val canonicalBase = baseDir.canonicalPath
                if (normalizedPath == canonicalBase ||
                    normalizedPath.startsWith("${canonicalBase}${File.separator}")
                ) {
                    return true
                }
            }
            Log.w(TAG, "Path validation failed: outside allowlist")
            return false
        } catch (e: IOException) {
            Log.e(TAG, "Path validation failed: normalize error", e)
            return false
        }
    }

    override fun copyFile(sourcePath: String?, targetPath: String?): Boolean {
        if (sourcePath.isNullOrBlank() || targetPath.isNullOrBlank()) return false
        // Validate both source and target paths
        if (!isPathAllowed(sourcePath) || !isPathAllowed(targetPath)) {
            Log.w(TAG, "copyFile: path validation failed - source: $sourcePath, target: $targetPath")
            return false
        }
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
        // Validate target path
        if (!isPathAllowed(targetPath)) {
            Log.w(TAG, "copyFileDescriptor: path validation failed for target: $targetPath")
            return false
        }
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
        // Validate path
        if (!isPathAllowed(path)) {
            Log.w(TAG, "deleteFile: path validation failed for: $path")
            return false
        }
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
        // Validate both source and target paths
        if (!isPathAllowed(sourcePath) || !isPathAllowed(targetPath)) {
            Log.w(TAG, "renameFile: path validation failed - source: $sourcePath, target: $targetPath")
            return false
        }
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
                target.setReadable(true, true)
                target.setWritable(true, true)
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
        // Validate path
        if (!isPathAllowed(path)) {
            Log.w(TAG, "fileSize: path validation failed for: $path")
            return -1L
        }
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

            target.setReadable(true, true)
            target.setWritable(true, true)
            backup.delete()
            Log.d(
                TAG,
                "replaceTarget: complete bytes=${target.length()} ms=${System.currentTimeMillis() - startedAt}"
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
