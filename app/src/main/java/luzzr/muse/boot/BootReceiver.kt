package luzzr.muse.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import luzzr.muse.work.PeriodicWorkScheduler

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            PeriodicWorkScheduler.schedule(context)
        }
    }
}
