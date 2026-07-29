package luzzr.muse.player

import android.content.SharedPreferences
import androidx.core.content.edit
import luzzr.muse.domain.model.Song
import luzzr.muse.media.PlaybackRepeatMode
import javax.inject.Singleton

@Singleton
class SessionPersistenceManager @javax.inject.Inject constructor() {

    private var sessionPrefs: SharedPreferences? = null
    private var lastSavedPlaylistHash: Int? = null

    fun initSessionPrefs(prefs: SharedPreferences) {
        sessionPrefs = prefs
        _shuffleModeOverride = prefs.getBoolean("shuffle_mode", false)
        _repeatModeOverride = prefs.getInt("repeat_mode", androidx.media3.common.Player.REPEAT_MODE_ALL)
    }

    private var _shuffleModeOverride: Boolean = false
    private var _repeatModeOverride: Int = androidx.media3.common.Player.REPEAT_MODE_ALL

    val shuffleModeOverride: Boolean get() = _shuffleModeOverride
    val repeatModeOverride: Int get() = _repeatModeOverride

    fun saveSession(currentPlaylist: List<Song>, currentIndex: Int, currentPosition: Long, shuffleMode: Boolean, repeatMode: Int) {
        val prefs = sessionPrefs ?: return
        val listHash = currentPlaylist.hashCode()
        prefs.edit {
            if (lastSavedPlaylistHash == null || lastSavedPlaylistHash != listHash) {
                val ids = currentPlaylist.map { it.id }
                putString("last_playlist_ids", ids.joinToString(","))
                putBoolean("has_session", ids.isNotEmpty())
                lastSavedPlaylistHash = listHash
            }

            putInt("last_index", currentIndex)
            putLong("last_position", currentPosition)
            putBoolean("shuffle_mode", shuffleMode)
            putInt("repeat_mode", repeatMode)
        }
    }

    fun hasSavedSession(): Boolean {
        return sessionPrefs?.getBoolean("has_session", false) == true
    }

    fun getSavedPlaylistIds(): List<Long> {
        val prefs = sessionPrefs ?: return emptyList()
        val raw = prefs.getString("last_playlist_ids", "") ?: ""
        return raw.split(",").filter { it.isNotBlank() }.mapNotNull { it.toLongOrNull() }
    }

    fun getSavedPlaybackInfo(): Pair<Int, Long> {
        val prefs = sessionPrefs ?: return Pair(0, 0L)
        return Pair(prefs.getInt("last_index", 0), prefs.getLong("last_position", 0L))
    }

    fun getSavedShuffleMode(): Boolean {
        return sessionPrefs?.getBoolean("shuffle_mode", false) ?: false
    }

    fun getSavedRepeatMode(): PlaybackRepeatMode {
        return (
            sessionPrefs?.getInt("repeat_mode", androidx.media3.common.Player.REPEAT_MODE_ALL)
                ?: androidx.media3.common.Player.REPEAT_MODE_ALL
            ).toPlaybackRepeatMode()
    }

    fun saveSongProgress(songId: Long, progress: Long) {
        val prefs = sessionPrefs ?: return
        prefs.edit { putLong("progress_$songId", progress) }
    }

    fun getSavedSongProgress(songId: Long): Long {
        val prefs = sessionPrefs ?: return 0L
        return prefs.getLong("progress_$songId", 0L)
    }

    fun updateSongLastPlayedTime(songId: Long) {
        val prefs = sessionPrefs ?: return
        prefs.edit { putLong("last_played_at_$songId", System.currentTimeMillis()) }
    }

    fun getSongLastPlayedTime(songId: Long): Long {
        val prefs = sessionPrefs ?: return 0L
        return prefs.getLong("last_played_at_$songId", 0L)
    }

    fun clearSavedSession() {
        sessionPrefs?.edit { clear() }
        lastSavedPlaylistHash = null
    }
}

internal fun Int.toPlaybackRepeatMode(): PlaybackRepeatMode = when (this) {
    androidx.media3.common.Player.REPEAT_MODE_OFF -> PlaybackRepeatMode.OFF
    androidx.media3.common.Player.REPEAT_MODE_ONE -> PlaybackRepeatMode.ONE
    else -> PlaybackRepeatMode.ALL
}

internal fun PlaybackRepeatMode.toPlayerRepeatMode(): Int = when (this) {
    PlaybackRepeatMode.OFF -> androidx.media3.common.Player.REPEAT_MODE_OFF
    PlaybackRepeatMode.ONE -> androidx.media3.common.Player.REPEAT_MODE_ONE
    PlaybackRepeatMode.ALL -> androidx.media3.common.Player.REPEAT_MODE_ALL
}
