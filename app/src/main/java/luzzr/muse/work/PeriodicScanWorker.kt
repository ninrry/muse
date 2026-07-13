package luzzr.muse.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import luzzr.muse.core.log.MuseLog
import luzzr.muse.domain.repository.SongRepository
import java.util.concurrent.TimeUnit

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
            // Only retry up to 3 times with exponential backoff
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    companion object {
        const val WORK_NAME = "periodic_scan"
        private const val SCAN_INTERVAL_HOURS = 6L

        /**
         * Schedule periodic library scan with proper constraints:
         * - Only run when device is idle (not in active use)
         * - Requires battery not low
         * - Exponential backoff on failures
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .setRequiresDeviceIdle(true)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<PeriodicScanWorker>(
                SCAN_INTERVAL_HOURS, TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    1, TimeUnit.MINUTES
                )
                .build()

            androidx.work.WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    workRequest
                )
        }
    }
}