package luzzr.muse.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

class ReadAlongPlaybackService : Service() {
    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, notification("同步阅读"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val title = intent?.getStringExtra(EXTRA_TITLE)?.takeIf { it.isNotBlank() } ?: "同步阅读"
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(title))
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?) = null

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun notification(title: String): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = launchIntent?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag())
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText("正在进行同步阅读")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "同步阅读", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun immutableFlag(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        PendingIntent.FLAG_IMMUTABLE
    } else {
        0
    }

    companion object {
        const val EXTRA_TITLE = "extra_title"
        private const val CHANNEL_ID = "readalong-playback"
        private const val NOTIFICATION_ID = 4108
    }
}
