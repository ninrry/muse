package luzzr.muse.media

interface PlaybackServiceStarter {
    fun startForegroundService(): Boolean
    fun toggleFloatingLyrics()
}
