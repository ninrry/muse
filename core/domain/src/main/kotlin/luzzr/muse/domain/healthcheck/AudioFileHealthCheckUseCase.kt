package luzzr.muse.domain.healthcheck

import luzzr.muse.domain.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data class representing the progress and result of an audio file health check.
 */
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

/**
 * Contract for audio file health checking.
 * Part of core:domain to avoid direct feature dependency on core:data.
 */
interface AudioFileHealthCheckUseCase {
    suspend operator fun invoke(
        songs: List<Song>,
        onProgress: suspend (AudioHealthProgress) -> Unit
    ): AudioHealthProgress
}
