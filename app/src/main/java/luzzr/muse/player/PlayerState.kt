package luzzr.muse.player

import android.content.SharedPreferences
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import luzzr.muse.data.model.Song

/**
 * Single source of truth for playback state and control.
 * Updated by MusicService (which attaches/detaches the ExoPlayer),
 * observed by ViewModels.
 */
class PlayerState {

    // --- Sleep timer ---

    val sleepTimer = SleepTimer()

    // --- Observation state ---

    private val _currentPlaylist = MutableStateFlow<List<Song>>(emptyList())
    val currentPlaylist: StateFlow<List<Song>> = _currentPlaylist.asStateFlow()

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _progress = MutableStateFlow(0L)
    val progress: StateFlow<Long> = _progress.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _shuffleMode = MutableStateFlow(false)
    val shuffleMode: StateFlow<Boolean> = _shuffleMode.asStateFlow()

    // --- Player reference (set by MusicService) ---

    @Volatile
    private var player: ExoPlayer? = null

    private val pendingOperations = mutableListOf<() -> Unit>()

    // --- Session persistence (survives process death) ---

    private var sessionPrefs: SharedPreferences? = null

    fun initSessionPrefs(prefs: SharedPreferences) {
        sessionPrefs = prefs
    }

    /** Save playlist IDs + current index to SharedPreferences for crash/task-kill recovery */
    fun saveSession() {
        val prefs = sessionPrefs ?: return
        val ids = _currentPlaylist.value.map { it.id }
        val idx = player?.currentMediaItemIndex ?: -1
        val pos = player?.currentPosition ?: 0L
        prefs.edit()
            .putString("last_playlist_ids", ids.joinToString(","))
            .putInt("last_index", idx)
            .putLong("last_position", pos)
            .putBoolean("has_session", ids.isNotEmpty())
            .apply()
        Log.d("PlayerState", "saveSession: ids=${ids.size} idx=$idx pos=$pos")
    }

    /** Check if there's a saved session to restore */
    fun hasSavedSession(): Boolean {
        return sessionPrefs?.getBoolean("has_session", false) == true
    }

    /** Get saved playlist IDs for restoration */
    fun getSavedPlaylistIds(): List<Long> {
        val prefs = sessionPrefs ?: return emptyList()
        val raw = prefs.getString("last_playlist_ids", "") ?: ""
        return raw.split(",").filter { it.isNotBlank() }.mapNotNull { it.toLongOrNull() }
    }

    /** Get saved playback index and position */
    fun getSavedPlaybackInfo(): Pair<Int, Long> {
        val prefs = sessionPrefs ?: return Pair(0, 0L)
        return Pair(prefs.getInt("last_index", 0), prefs.getLong("last_position", 0L))
    }

    fun clearSavedSession() {
        sessionPrefs?.edit()?.clear()?.apply()
    }

    fun attachPlayer(exoPlayer: ExoPlayer) {
        player = exoPlayer
        android.util.Log.d("PlayerState", "attachPlayer: attached. pendingOps=${pendingOperations.size}")
        // Execute any pending operations
        pendingOperations.toList().forEach { it() }
        pendingOperations.clear()
    }

    fun detachPlayer() {
        android.util.Log.w("PlayerState", "detachPlayer: player detached (service destroyed)")
        player = null
        _isPlaying.value = false
        _progress.value = 0L
    }

    /**
     * Clear any queued operations that were waiting for a player attach.
     * Useful after process death / task removal, where old queued ops can pile up.
     */
    fun clearPendingOperations() {
        if (pendingOperations.isNotEmpty()) {
            android.util.Log.w("PlayerState", "clearPendingOperations: cleared ${pendingOperations.size} ops")
        }
        pendingOperations.clear()
    }

    // --- Control methods (called by ViewModels) ---

    fun playSongs(songs: List<Song>, startIndex: Int = 0, enableShuffle: Boolean = false) {
        val p = player
        if (p == null) {
            android.util.Log.w("PlayerState", "playSongs: player=null, queue op (songs=${songs.size}, startIndex=$startIndex)")
            // Player not ready yet — queue operation for when attachPlayer() is called
            pendingOperations.add { playSongs(songs, startIndex, enableShuffle) }
            return
        }
        android.util.Log.d("PlayerState", "playSongs: setMediaItems songs=${songs.size}, startIndex=$startIndex, enableShuffle=$enableShuffle")
        _currentPlaylist.value = songs

        val mediaItems = songs.map { song ->
            MediaItem.Builder()
                .setMediaId(song.id.toString())
                .setUri(song.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(song.title)
                        .setArtist(song.artist)
                        .setAlbumTitle(song.album)
                        .apply { song.artworkUri?.let { setArtworkUri(it) } }
                        .build()
                )
                .build()
        }

        p.setMediaItems(mediaItems, startIndex, C.TIME_UNSET)

        // Re-apply shuffle mode: either forced on, or preserve current state
        val targetShuffle = enableShuffle || _shuffleMode.value
        if (targetShuffle) {
            p.shuffleModeEnabled = true
        }

        p.prepare()
        p.play()
        saveSession()
    }

    fun togglePlayPause() {
        val p = player
        if (p == null) {
            android.util.Log.w("PlayerState", "togglePlayPause: player=null, queue op")
            // Player not ready yet — queue operation
            pendingOperations.add { togglePlayPause() }
            return
        }
        android.util.Log.d("PlayerState", "togglePlayPause: isPlaying=${_isPlaying.value}")
        if (_isPlaying.value) p.pause() else p.play()
    }

    fun seekTo(position: Long) {
        player?.seekTo(position)
    }

    fun skipToNext() {
        player?.seekToNextMediaItem()
    }

    fun skipToPrevious() {
        player?.seekToPreviousMediaItem()
    }

    fun setRepeatMode(mode: Int) {
        player?.repeatMode = mode
    }

    fun toggleShuffle() {
        player?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }

    /** Play all songs shuffled — picks a random start and enables ExoPlayer shuffle mode */
    fun playShuffled(songs: List<Song>) {
        if (songs.isEmpty()) return
        playSongs(songs, songs.indices.random(), enableShuffle = true)
    }

    // --- Internal update methods (called by MusicService listener) ---

    internal fun updateCurrentSong(song: Song?) { _currentSong.value = song }

    /**
     * Update a specific song in the current playlist and optionally update currentSong
     * if the song matches the currently playing song. Used after async artwork generation.
     */
    internal fun updateSongInPlaylist(index: Int, updatedSong: Song) {
        val list = _currentPlaylist.value.toMutableList()
        if (index in list.indices) {
            list[index] = updatedSong
            _currentPlaylist.value = list
            // If this is the currently playing song, also update currentSong
            if (_currentSong.value?.id == updatedSong.id) {
                _currentSong.value = updatedSong
            }
        }
    }
    internal fun updateIsPlaying(playing: Boolean) { _isPlaying.value = playing }
    internal fun updateProgress(progress: Long) { _progress.value = progress }
    internal fun updateDuration(duration: Long) { _duration.value = duration }
    internal fun updateRepeatMode(mode: Int) { _repeatMode.value = mode }
    internal fun updateShuffleMode(enabled: Boolean) { _shuffleMode.value = enabled }
}
