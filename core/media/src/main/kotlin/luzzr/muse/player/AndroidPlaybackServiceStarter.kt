package luzzr.muse.player

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import luzzr.muse.core.log.MuseLog
import luzzr.muse.media.PlaybackServiceStarter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidPlaybackServiceStarter @Inject constructor(
    @ApplicationContext private val context: Context
) : PlaybackServiceStarter {

    override fun startForegroundService(): Boolean {
        return try {
            context.startForegroundService(Intent(context, MusicService::class.java))
            true
        } catch (e: IllegalStateException) {
            MuseLog.e("PlaybackServiceStarter", "Failed to start foreground playback service", e)
            false
        } catch (e: SecurityException) {
            MuseLog.e("PlaybackServiceStarter", "Missing permission to start foreground playback service", e)
            false
        }
    }

}
