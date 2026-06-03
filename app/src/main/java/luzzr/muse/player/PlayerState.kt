package luzzr.muse.player

import android.content.SharedPreferences
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import luzzr.muse.core.log.MuseLog
import luzzr.muse.data.model.Song
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth for playback state and control.
 * Updated by MusicService (which attaches/detaches the ExoPlayer),
 * observed by ViewModels.
 */
class PlayerState {

    // --- Sleep timer ---

    val sleepTimer = SleepTimer()

    // --- Observation state ---

    private var originalPlaylist: List<Song> = emptyList()

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

    // --- Floating lyrics overlay ---

    private val _floatingLyricsEnabled = MutableStateFlow(false)
    val floatingLyricsEnabled: StateFlow<Boolean> = _floatingLyricsEnabled.asStateFlow()

    /** Lyrics data published by PlayerViewModel for outer consumption (notification overlay, etc.) */
    private val _currentLyrics = MutableStateFlow<List<luzzr.muse.data.network.LrcLine>>(emptyList())
    val currentLyrics: StateFlow<List<luzzr.muse.data.network.LrcLine>> = _currentLyrics.asStateFlow()

    private val _currentLyricLine = MutableStateFlow(-1)
    val currentLyricLine: StateFlow<Int> = _currentLyricLine.asStateFlow()

    // --- Player reference (set by MusicService) ---

    @Volatile
    private var player: ExoPlayer? = null

    private val pendingOperations = mutableListOf<() -> Unit>()

    // --- Session persistence (survives process death) ---

    private var sessionPrefs: SharedPreferences? = null
    private var lastSavedPlaylistHash: Int? = null

    fun initSessionPrefs(prefs: SharedPreferences) {
        sessionPrefs = prefs
    }

    /** Save playlist IDs + current index + shuffle mode for crash/task-kill recovery */
    fun saveSession() {
        val prefs = sessionPrefs ?: return
        val currentList = _currentPlaylist.value
        val listHash = currentList.hashCode()
        
        val editor = prefs.edit()
        if (lastSavedPlaylistHash == null || lastSavedPlaylistHash != listHash) {
            val ids = currentList.map { it.id }
            editor.putString("last_playlist_ids", ids.joinToString(","))
            editor.putBoolean("has_session", ids.isNotEmpty())
            lastSavedPlaylistHash = listHash
        }
        
        val idx = player?.currentMediaItemIndex ?: -1
        val pos = player?.currentPosition ?: 0L
        editor.putInt("last_index", idx)
        editor.putLong("last_position", pos)
        editor.putBoolean("shuffle_mode", _shuffleMode.value)
        editor.apply()
        MuseLog.d("PlayerState", "saveSession: idx=$idx pos=$pos shuffle=${_shuffleMode.value} listHash=$listHash")
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

    /** Get saved shuffle mode */
    fun getSavedShuffleMode(): Boolean {
        return sessionPrefs?.getBoolean("shuffle_mode", false) ?: false
    }

    fun saveSongProgress(songId: Long, progress: Long) {
        val prefs = sessionPrefs ?: return
        prefs.edit().putLong("progress_$songId", progress).apply()
    }

    fun getSavedSongProgress(songId: Long): Long {
        val prefs = sessionPrefs ?: return 0L
        return prefs.getLong("progress_$songId", 0L)
    }

    fun clearSavedSession() {
        sessionPrefs?.edit()?.clear()?.apply()
        lastSavedPlaylistHash = null
    }

    fun attachPlayer(exoPlayer: ExoPlayer) {
        player = exoPlayer
        exoPlayer.repeatMode = _repeatMode.value
        exoPlayer.shuffleModeEnabled = _shuffleMode.value
        MuseLog.d("PlayerState", "attachPlayer: attached. pendingOps=${pendingOperations.size}")
        // Execute any pending operations
        pendingOperations.toList().forEach { it() }
        pendingOperations.clear()
    }

    fun detachPlayer() {
        MuseLog.w("PlayerState", "detachPlayer: player detached (service destroyed)")
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
            MuseLog.w("PlayerState", "clearPendingOperations: cleared ${pendingOperations.size} ops")
        }
        pendingOperations.clear()
    }

    // --- Control methods (called by ViewModels) ---

    private fun Song.toMediaItem(): MediaItem {
        return MediaItem.Builder()
            .setMediaId(id.toString())
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .apply {
                        artworkUri
                            ?.takeUnless { it.scheme?.lowercase() == "file" }
                            ?.let { setArtworkUri(it) }
                    }
                    .build()
            )
            .build()
    }

    fun playSongs(songs: List<Song>, startIndex: Int = 0, enableShuffle: Boolean = false) {
        if (songs.isEmpty()) {
            _currentPlaylist.value = emptyList()
            originalPlaylist = emptyList()
            _currentSong.value = null
            _isPlaying.value = false
            return
        }

        val playableSongs = songs.map { it.withUsableLocalArtwork() }
        originalPlaylist = playableSongs
        val safeStartIndex = startIndex.coerceIn(playableSongs.indices)

        val targetShuffle = enableShuffle || _shuffleMode.value
        _shuffleMode.value = targetShuffle

        _currentPlaylist.value = playableSongs
        _currentSong.value = playableSongs[safeStartIndex]
        _progress.value = 0L
        _duration.value = playableSongs[safeStartIndex].duration

        val p = player
        if (p == null) {
            MuseLog.w("PlayerState", "playSongs: player=null, queue op (songs=${playableSongs.size})")
            pendingOperations.add { playSongs(songs, startIndex, enableShuffle) }
            return
        }
        MuseLog.d(
            "PlayerState",
            "playSongs: setMediaItems songs=${playableSongs.size}, startIndex=$safeStartIndex, shuffleMode=$targetShuffle"
        )

        val mediaItems = playableSongs.map { it.toMediaItem() }

        val targetSong = playableSongs.getOrNull(safeStartIndex)
        val startPos = if (targetSong != null && targetSong.duration >= 600_000) {
            getSavedSongProgress(targetSong.id)
        } else {
            C.TIME_UNSET
        }

        p.setMediaItems(mediaItems, safeStartIndex, startPos)
        p.repeatMode = _repeatMode.value
        p.shuffleModeEnabled = targetShuffle

        p.prepare()
        p.play()
        saveSession()
    }

    private fun Song.withUsableLocalArtwork(): Song {
        val artwork = artworkUri ?: return this
        if (artwork.scheme?.lowercase() != "file") return this
        val exists = artwork.path?.let { File(it).exists() } == true
        return if (exists) this else copy(artworkUri = null)
    }

    fun togglePlayPause() {
        val p = player
        if (p == null) {
            MuseLog.w("PlayerState", "togglePlayPause: player=null, queue op")
            // Player not ready yet ?queue operation
            pendingOperations.add { togglePlayPause() }
            return
        }
        MuseLog.d("PlayerState", "togglePlayPause: isPlaying=${_isPlaying.value}")
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
        _repeatMode.value = mode
        player?.repeatMode = mode
    }

    fun toggleShuffle() {
        val nextShuffle = !_shuffleMode.value
        _shuffleMode.value = nextShuffle

        val p = player ?: return
        p.shuffleModeEnabled = nextShuffle
        saveSession()
    }

    /** Play all songs shuffled ?picks a random start and enables ExoPlayer shuffle mode */
    fun playShuffled(songs: List<Song>) {
        if (songs.isEmpty()) return
        playSongs(songs, songs.indices.random(), enableShuffle = true)
    }

    fun regenerateShuffleQueueAndPlay() {
        val p = player ?: return
        val original = originalPlaylist.map { it.withUsableLocalArtwork() }
        if (original.isNotEmpty()) {
            val shuffledList = original.shuffled()
            _currentPlaylist.value = shuffledList

            val mediaItems = shuffledList.map { it.toMediaItem() }
            p.setMediaItems(mediaItems, 0, C.TIME_UNSET)
            p.prepare()
            p.play()
            saveSession()
        }
    }

    // --- Internal update methods (called by MusicService listener) ---

    internal fun updateCurrentSong(song: Song?) {
        _currentSong.value = song
    }

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
    internal fun updateIsPlaying(playing: Boolean) {
        _isPlaying.value = playing
    }
    internal fun updateProgress(progress: Long) {
        _progress.value = progress
    }
    internal fun updateDuration(duration: Long) {
        _duration.value = duration
    }
    internal fun updateRepeatMode(mode: Int) {
        _repeatMode.value = mode
    }
    internal fun updateShuffleMode(enabled: Boolean) {
        _shuffleMode.value = enabled
    }

    // --- Floating lyrics internal updates ---

    internal fun updateFloatingLyricsEnabled(enabled: Boolean) {
        _floatingLyricsEnabled.value = enabled
    }

    internal fun updateCurrentLyrics(lyrics: List<luzzr.muse.data.network.LrcLine>) {
        _currentLyrics.value = lyrics
    }

    internal fun updateCurrentLyricLine(line: Int) {
        _currentLyricLine.value = line
    }

    fun toggleFloatingLyrics() {
        _floatingLyricsEnabled.value = !_floatingLyricsEnabled.value
    }
}
