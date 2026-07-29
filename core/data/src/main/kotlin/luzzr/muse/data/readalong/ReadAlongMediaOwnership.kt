package luzzr.muse.data.readalong

import java.io.File
import java.security.MessageDigest
import luzzr.muse.data.database.ReadAlongBookEntity
import luzzr.muse.domain.model.Song
import org.json.JSONArray

/**
 * Separates chapter audio imported for synchronized reading from the user's music
 * library. An imported book keeps a private copy of each chapter audio file; a
 * source copy can still be indexed by MediaStore or the Downloads fallback scan.
 *
 * Ownership is determined by byte size and SHA-256, never filename or directory.
 * That makes the boundary robust across SAF, MediaStore, moved folders, and
 * duplicate filenames while avoiding a hash read for ordinary songs whose sizes
 * do not match a read-along chapter asset.
 */
internal class ReadAlongMediaOwnershipIndex private constructor(
    private val hashesBySize: Map<Long, Set<String>>
) {
    fun owns(song: Song): Boolean = owns(File(song.filePath), song.size)

    fun owns(file: File, declaredSize: Long = file.length()): Boolean {
        val expected = hashesBySize[declaredSize.takeIf { it > 0L } ?: return false] ?: return false
        if (!file.isFile) return false
        return file.sha256() in expected
    }

    companion object {
        fun fromBooks(books: List<ReadAlongBookEntity>): ReadAlongMediaOwnershipIndex {
            val hashes = linkedMapOf<Long, MutableSet<String>>()
            books.forEach { book ->
                runCatching { JSONArray(book.chaptersJson) }.getOrNull()?.let { chapters ->
                    for (index in 0 until chapters.length()) {
                        val chapter = chapters.optJSONObject(index) ?: continue
                        val audioPath = chapter.optString("audioPath").takeIf(String::isNotBlank)
                        val audioFile = audioPath?.let(::File)?.takeIf(File::isFile)
                        val size = chapter.optLong("audioByteSize", 0L)
                            .takeIf { it > 0L }
                            ?: audioFile?.length()
                            ?: continue
                        // Old imported books did not persist the hash. Compute it from the
                        // app-private copy once so the same isolation rule also repairs them.
                        val hash = chapter.optString("audioSha256").takeIf(String::isNotBlank)
                            ?: audioFile?.sha256()
                            ?: continue
                        hashes.getOrPut(size) { linkedSetOf() }.add(hash)
                    }
                }
            }
            return ReadAlongMediaOwnershipIndex(hashes)
        }
    }
}

private fun File.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}
