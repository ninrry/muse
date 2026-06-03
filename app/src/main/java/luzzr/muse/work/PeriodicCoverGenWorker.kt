package luzzr.muse.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import luzzr.muse.core.log.MuseLog
import luzzr.muse.data.repository.ArtworkRepository

@HiltWorker
class PeriodicCoverGenWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val artworkRepo: ArtworkRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            artworkRepo.generateDefaultCoversForAll()
            Result.success()
        } catch (e: Exception) {
            MuseLog.e("PeriodicCoverGenWorker", "Cover gen failed", e)
            Result.retry()
        }
    }
}
