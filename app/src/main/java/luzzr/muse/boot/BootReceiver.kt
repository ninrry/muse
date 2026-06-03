package luzzr.muse.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import luzzr.muse.work.PeriodicScanWorker
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val workManager = WorkManager.getInstance(context)
            workManager.enqueueUniquePeriodicWork(
                "periodic_scan",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<PeriodicScanWorker>(1, TimeUnit.DAYS).build()
            )
        }
    }
}
