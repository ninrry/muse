package luzzr.muse.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import luzzr.muse.core.log.MuseLog
import luzzr.muse.domain.repository.SongRepository

@HiltWorker
class PeriodicScanWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val songRepository: SongRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            songRepository.scanAll()
            Result.success()
        } catch (e: Exception) {
            MuseLog.e("PeriodicScanWorker", "Scan failed", e)
            Result.retry()
        }
    }
}
