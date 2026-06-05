package luzzr.muse.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import luzzr.muse.core.log.MuseLog
import luzzr.muse.domain.repository.SongRepository
import luzzr.muse.media.PlaybackController

@HiltWorker
class SessionRestoreWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val playbackController: PlaybackController,
    private val songRepository: SongRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val ids = playbackController.getSavedPlaylistIds()
            if (ids.isNotEmpty()) {
                val songs = songRepository.loadFromDatabase()
                val savedSongs = ids.mapNotNull { id -> songs.find { it.id == id } }
                if (savedSongs.isNotEmpty()) {
                    playbackController.playSongs(savedSongs, 0)
                }
            }
            Result.success()
        } catch (e: Exception) {
            MuseLog.e("SessionRestoreWorker", "Restore failed", e)
            Result.retry()
        }
    }
}
