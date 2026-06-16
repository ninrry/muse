package luzzr.muse.data.tag

import luzzr.muse.domain.model.Song
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AudioHealthProgress(
    val total: Int,
    val current: Int,
    val healthy: Int,
    val damaged: Int,
    val unsupported: Int,
    val missing: Int,
    val currentName: String,
    val examples: List<String> = emptyList()
) {
    val isFinished: Boolean get() = total > 0 && current >= total
}

@Singleton
class AudioFileHealthCheckUseCase @Inject constructor(
    private val tagEditor: TagEditor
) {
    suspend operator fun invoke(
        songs: List<Song>,
        onProgress: suspend (AudioHealthProgress) -> Unit
    ): AudioHealthProgress = withContext(Dispatchers.IO) {
        var healthy = 0
        var damaged = 0
        var unsupported = 0
        var missing = 0
        val examples = mutableListOf<String>()
        val total = songs.size

        if (total == 0) {
            return@withContext AudioHealthProgress(0, 0, 0, 0, 0, 0, "")
        }

        for ((index, song) in songs.withIndex()) {
            val file = File(song.filePath)
            val ext = file.extension.lowercase()
            val problem = when {
                song.filePath.isBlank() || !file.exists() || file.length() <= 0L -> {
                    missing++
                    "缺失或空文件"
                }
                ext.isNotBlank() && ext !in SUPPORTED_TAG_EXTENSIONS -> {
                    unsupported++
                    "扩展名不支持"
                }
                tagEditor.canReadAudioFile(file.absolutePath) || tagEditor.hasRecognizedAudioHeader(file.absolutePath) -> {
                    healthy++
                    null
                }
                else -> {
                    damaged++
                    "标签结构异常或文件损坏"
                }
            }

            if (problem != null && examples.size < MAX_EXAMPLES) {
                examples += "${song.title.ifBlank { file.name }}：$problem"
            }

            onProgress(
                AudioHealthProgress(
                    total = total,
                    current = index + 1,
                    healthy = healthy,
                    damaged = damaged,
                    unsupported = unsupported,
                    missing = missing,
                    currentName = song.title.ifBlank { file.name },
                    examples = examples.toList()
                )
            )
        }

        AudioHealthProgress(
            total = total,
            current = total,
            healthy = healthy,
            damaged = damaged,
            unsupported = unsupported,
            missing = missing,
            currentName = "",
            examples = examples.toList()
        )
    }

    private companion object {
        val SUPPORTED_TAG_EXTENSIONS = setOf("mp3", "flac", "ogg", "oga", "opus", "m4a", "m4b", "mp4", "alac", "wav")
        const val MAX_EXAMPLES = 5
    }
}
