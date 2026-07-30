package luzzr.muse.data.tag

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.provider.MediaStore
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import luzzr.muse.core.log.MuseLog
import luzzr.muse.data.database.SongDao
import luzzr.muse.domain.model.Song
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class RenameProgress(
    val total: Int,
    val current: Int,
    val currentName: String,
    val success: Int,
    val failed: Int
)

@Singleton
class RenameSongsToTagUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val songDao: SongDao
) {
    suspend operator fun invoke(songs: List<Song>, onProgress: suspend (RenameProgress) -> Unit): RenameProgress =
        withContext(Dispatchers.IO) {
            var success = 0
            var failed = 0
            val total = songs.size

            for ((index, song) in songs.withIndex()) {
                val newName = buildFileName(song)
                if (newName == null || newName == File(song.filePath).name) {
                    failed++
                    continue
                }

                val result = renameFile(song, newName)
                if (result) success++ else failed++

                onProgress(RenameProgress(total, index + 1, newName, success, failed))
            }

            RenameProgress(total, total, "", success, failed)
        }

    private fun buildFileName(song: Song): String? {
        val title = song.title.takeIf { it.isNotBlank() } ?: return null
        val artist = song.artist.takeIf { it.isNotBlank() }
        val safeTitle = sanitize(title)
        val safeArtist = artist?.let { sanitize(it) }
        val ext = File(song.filePath).extension.ifBlank { "mp3" }
        return if (safeArtist != null) "$safeArtist - $safeTitle.$ext" else "$safeTitle.$ext"
    }

    private fun sanitize(name: String) = name.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().take(120)

    private suspend fun renameFile(song: Song, newName: String): Boolean = try {
        val oldFile = File(song.filePath)
        if (!oldFile.exists()) return false
        val parent = oldFile.parentFile ?: return false
        val newFile = resolveConflict(parent, newName)
        val renamed = oldFile.renameTo(newFile) || renameWithMediaStore(song, newFile.name)
        if (renamed) {
            MediaScannerConnection.scanFile(context, arrayOf(newFile.absolutePath), null, null)
            songDao.updateSongFilePath(song.id, newFile.absolutePath, song.uri)
            true
        } else {
            false
        }
    } catch (e: Exception) {
        MuseLog.e("RenameSongsToTag", "failed: ${song.filePath}", e)
        false
    }

    private fun renameWithMediaStore(song: Song, displayName: String): Boolean {
        return try {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
            }
            context.contentResolver.update(song.uri.toUri(), values, null, null) > 0
        } catch (e: Exception) {
            MuseLog.e("RenameSongsToTag", "MediaStore rename failed: ${song.filePath}", e)
            false
        }
    }

    private fun resolveConflict(parent: File, name: String): File {
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        val extPart = if (ext.isNotEmpty() && ext != base) ".$ext" else ""
        var f = File(parent, "$base$extPart")
        var c = 1
        while (f.exists()) {
            f = File(parent, "${base}_($c)$extPart")
            c++
        }
        return f
    }
}
