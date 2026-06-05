package luzzr.muse.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object PeriodicWorkScheduler {
    private const val PERIODIC_SCAN_WORK = "periodic_scan"
    private const val PERIODIC_COVER_WORK = "periodic_cover_generation"
    private const val SCHEDULER_PREFS = "periodic_work_scheduler"
    private const val KEY_MAINTENANCE_WORK_VERSION = "maintenance_work_version"
    private const val MAINTENANCE_WORK_VERSION = 2
    private const val SCAN_INITIAL_DELAY_HOURS = 6L
    private const val COVER_INITIAL_DELAY_DAYS = 1L

    fun schedule(context: Context) {
        val appContext = context.applicationContext
        val workManager = WorkManager.getInstance(appContext)
        val schedulerPrefs = appContext.getSharedPreferences(SCHEDULER_PREFS, Context.MODE_PRIVATE)
        val maintenanceWorkPolicy =
            if (schedulerPrefs.getInt(KEY_MAINTENANCE_WORK_VERSION, 0) < MAINTENANCE_WORK_VERSION) {
                ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE
            } else {
                ExistingPeriodicWorkPolicy.KEEP
            }
        val maintenanceConstraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()

        workManager.enqueueUniquePeriodicWork(
            PERIODIC_SCAN_WORK,
            maintenanceWorkPolicy,
            PeriodicWorkRequestBuilder<PeriodicScanWorker>(1, TimeUnit.DAYS)
                .setConstraints(maintenanceConstraints)
                .setInitialDelay(SCAN_INITIAL_DELAY_HOURS, TimeUnit.HOURS)
                .build()
        )
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_COVER_WORK,
            maintenanceWorkPolicy,
            PeriodicWorkRequestBuilder<PeriodicCoverGenWorker>(7, TimeUnit.DAYS)
                .setConstraints(maintenanceConstraints)
                .setInitialDelay(COVER_INITIAL_DELAY_DAYS, TimeUnit.DAYS)
                .build()
        )
        schedulerPrefs.edit()
            .putInt(KEY_MAINTENANCE_WORK_VERSION, MAINTENANCE_WORK_VERSION)
            .apply()
    }
}
