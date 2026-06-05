package luzzr.muse.player

import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import luzzr.muse.core.log.MuseLog
import luzzr.muse.domain.model.MediaClassifier
import luzzr.muse.domain.model.Song
import luzzr.muse.media.PlaybackController
import luzzr.muse.media.PlaybackRepeatMode
import luzzr.muse.media.PlaybackState
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Single source of truth for playback state and control.
 * Updated by MusicService (which attaches/detaches the ExoPlayer),
 * observed by ViewModels.
 */
class PlayerState : PlaybackController {

    // --- Sleep timer ---

    override val sleepTimer = SleepTimer()

    // --- Observation state ---

    private var originalPlaylist: List<Song> = emptyList()

    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

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

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_ALL)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _shuffleMode = MutableStateFlow(false)
    val shuffleMode: StateFlow<Boolean> = _shuffleMode.asStateFlow()

    // --- Floating lyrics overlay ---

    private val _floatingLyricsEnabled = MutableStateFlow(false)
    val floatingLyricsEnabled: StateFlow<Boolean> = _floatingLyricsEnabled.asStateFlow()

    /** Lyrics data published by PlayerViewModel for outer consumption (notification overlay, etc.) */
    private val _currentLyrics = MutableStateFlow<List<luzzr.muse.domain.model.LrcLine>>(emptyList())
    val currentLyrics: StateFlow<List<luzzr.muse.domain.model.LrcLine>> = _currentLyrics.asStateFlow()

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
        val shuffleEnabled = prefs.getBoolean("shuffle_mode", false)
        val repeatMode = prefs.getInt("repeat_mode", Player.REPEAT_MODE_ALL)
        _shuffleMode.value = shuffleEnabled
        _repeatMode.value = repeatMode
        _state.update {
            it.copy(
                shuffleEnabled = shuffleEnabled,
                repeatMode = repeatMode.toPlaybackRepeatMode()
            )
        }
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
        editor.putInt("repeat_mode", _repeatMode.value)
        editor.apply()
        MuseLog.d(
            "PlayerState",
            "saveSession: idx=$idx pos=$pos shuffle=${_shuffleMode.value} " +
                "repeat=${_repeatMode.value} listHash=$listHash"
        )
    }

    /** Check if there's a saved session to restore */
    override fun hasSavedSession(): Boolean {
        return sessionPrefs?.getBoolean("has_session", false) == true
    }

    /** Get saved playlist IDs for restoration */
    override fun getSavedPlaylistIds(): List<Long> {
        val prefs = sessionPrefs ?: return emptyList()
        val raw = prefs.getString("last_playlist_ids", "") ?: ""
        return raw.split(",").filter { it.isNotBlank() }.mapNotNull { it.toLongOrNull() }
    }

    /** Get saved playback index and position */
    override fun getSavedPlaybackInfo(): Pair<Int, Long> {
        val prefs = sessionPrefs ?: return Pair(0, 0L)
        return Pair(prefs.getInt("last_index", 0), prefs.getLong("last_position", 0L))
    }

    /** Get saved shuffle mode */
    override fun getSavedShuffleMode(): Boolean {
        return sessionPrefs?.getBoolean("shuffle_mode", false) ?: false
    }

    override fun getSavedRepeatMode(): PlaybackRepeatMode {
        return (sessionPrefs?.getInt("repeat_mode", Player.REPEAT_MODE_ALL) ?: Player.REPEAT_MODE_ALL)
            .toPlaybackRepeatMode()
    }

    fun saveSongProgress(songId: Long, progress: Long) {
        val prefs = sessionPrefs ?: return
        prefs.edit { putLong("progress_$songId", progress) }
    }

    override fun getSavedSongProgress(songId: Long): Long {
        val prefs = sessionPrefs ?: return 0L
        return prefs.getLong("progress_$songId", 0L)
    }

    fun updateSongLastPlayedTime(songId: Long) {
        val prefs = sessionPrefs ?: return
        prefs.edit { putLong("last_played_at_$songId", System.currentTimeMillis()) }
    }

    override fun getSongLastPlayedTime(songId: Long): Long {
        val prefs = sessionPrefs ?: return 0L
        return prefs.getLong("last_played_at_$songId", 0L)
    }

    override fun clearSavedSession() {
        sessionPrefs?.edit { clear() }
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
        _state.update { it.copy(isPlaying = false, positionMs = 0L) }
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
                            ?.toUri()
                            ?.takeUnless { it.scheme?.lowercase() == "file" }
                            ?.let(::setArtworkUri)
                    }
                    .build()
            )
            .build()
    }

    override fun playSongs(songs: List<Song>, startIndex: Int, enableShuffle: Boolean) {
        if (songs.isEmpty()) {
            _currentPlaylist.value = emptyList()
            originalPlaylist = emptyList()
            _currentSong.value = null
            _isPlaying.value = false
            _state.value = PlaybackState()
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
        _state.update {
            it.copy(
                playlist = playableSongs,
                currentSong = playableSongs[safeStartIndex],
                positionMs = 0L,
                durationMs = playableSongs[safeStartIndex].duration,
                shuffleEnabled = targetShuffle
            )
        }

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
        val startPos = if (targetSong != null && MediaClassifier.isAudiobook(targetSong)) {
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
        val artworkUri = artwork.toUri()
        if (artworkUri.scheme?.lowercase() != "file") return this
        val exists = artworkUri.path?.let { File(it).exists() } == true
        return if (exists) this else copy(artworkUri = null)
    }

    override fun togglePlayPause() {
        val p = player
        if (p == null) {
            MuseLog.w("PlayerState", "togglePlayPause: player=null, queue op")
            // Player not ready yet – queue operation
            pendingOperations.add { togglePlayPause() }
            return
        }
        MuseLog.d("PlayerState", "togglePlayPause: isPlaying=${_isPlaying.value} mediaItemCount=${p.mediaItemCount}")
        if (p.mediaItemCount == 0 && _currentPlaylist.value.isNotEmpty()) {
            val currentList = _currentPlaylist.value
            val currentSongIndex = currentList.indexOfFirst { it.id == _currentSong.value?.id }.coerceAtLeast(0)
            playSongs(currentList, currentSongIndex, enableShuffle = _shuffleMode.value)
            return
        }
        if (_isPlaying.value) p.pause() else p.play()
    }

    override fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs)
    }

    override fun skipToNext() {
        player?.seekToNextMediaItem()
    }

    override fun skipToPrevious() {
        player?.seekToPreviousMediaItem()
    }

    override fun setRepeatMode(mode: PlaybackRepeatMode) {
        val playerMode = mode.toPlayerRepeatMode()
        _repeatMode.value = playerMode
        _state.update { it.copy(repeatMode = mode) }
        player?.repeatMode = playerMode
        saveSession()
    }

    fun setRepeatMode(mode: Int) {
        setRepeatMode(mode.toPlaybackRepeatMode())
    }

    override fun toggleShuffle() {
        val nextShuffle = !_shuffleMode.value
        _shuffleMode.value = nextShuffle
        _state.update { it.copy(shuffleEnabled = nextShuffle) }

        val p = player ?: return
        p.shuffleModeEnabled = nextShuffle
        saveSession()
    }

    /** Play all songs shuffled ?picks a random start and enables ExoPlayer shuffle mode */
    override fun playShuffled(songs: List<Song>) {
        if (songs.isEmpty()) return
        playSongs(songs, songs.indices.random(), enableShuffle = true)
    }

    override fun regenerateShuffleQueueAndPlay() {
        val p = player ?: return
        val original = originalPlaylist.map { it.withUsableLocalArtwork() }
        if (original.isNotEmpty()) {
            val shuffledList = original.shuffled()
            _currentPlaylist.value = shuffledList
            _state.update { it.copy(playlist = shuffledList) }

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
        _state.update { it.copy(currentSong = song) }
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
            _state.update { it.copy(playlist = list) }
            // If this is the currently playing song, also update currentSong
            if (_currentSong.value?.id == updatedSong.id) {
                _currentSong.value = updatedSong
                _state.update { it.copy(currentSong = updatedSong) }
            }
        }
    }
    internal fun updateIsPlaying(playing: Boolean) {
        _isPlaying.value = playing
        _state.update { it.copy(isPlaying = playing) }
    }
    internal fun updateProgress(progress: Long) {
        _progress.value = progress
        _state.update { it.copy(positionMs = progress) }
    }
    internal fun updateDuration(duration: Long) {
        _duration.value = duration
        _state.update { it.copy(durationMs = duration) }
    }
    internal fun updateRepeatMode(mode: Int) {
        _repeatMode.value = mode
        _state.update { it.copy(repeatMode = mode.toPlaybackRepeatMode()) }
    }
    internal fun updateShuffleMode(enabled: Boolean) {
        _shuffleMode.value = enabled
        _state.update { it.copy(shuffleEnabled = enabled) }
    }

    // --- Floating lyrics internal updates ---

    internal fun updateFloatingLyricsEnabled(enabled: Boolean) {
        _floatingLyricsEnabled.value = enabled
        _state.update { it.copy(floatingLyricsEnabled = enabled) }
    }

    internal fun updateCurrentLyrics(lyrics: List<luzzr.muse.domain.model.LrcLine>) {
        _currentLyrics.value = lyrics
        _state.update { it.copy(lyrics = lyrics) }
    }

    internal fun updateCurrentLyricLine(line: Int) {
        _currentLyricLine.value = line
        _state.update { it.copy(currentLyricLine = line) }
    }

    fun toggleFloatingLyrics() {
        _floatingLyricsEnabled.value = !_floatingLyricsEnabled.value
        _state.update { it.copy(floatingLyricsEnabled = _floatingLyricsEnabled.value) }
    }

    override fun refreshCurrentSong(song: Song) {
        updateCurrentSong(song)
    }

    override fun publishLyrics(lyrics: List<luzzr.muse.domain.model.LrcLine>) {
        updateCurrentLyrics(lyrics)
    }

    override fun publishCurrentLyricLine(line: Int) {
        updateCurrentLyricLine(line)
    }
}

private fun Int.toPlaybackRepeatMode(): PlaybackRepeatMode = when (this) {
    Player.REPEAT_MODE_OFF -> PlaybackRepeatMode.OFF
    Player.REPEAT_MODE_ONE -> PlaybackRepeatMode.ONE
    else -> PlaybackRepeatMode.ALL
}

private fun PlaybackRepeatMode.toPlayerRepeatMode(): Int = when (this) {
    PlaybackRepeatMode.OFF -> Player.REPEAT_MODE_OFF
    PlaybackRepeatMode.ONE -> Player.REPEAT_MODE_ONE
    PlaybackRepeatMode.ALL -> Player.REPEAT_MODE_ALL
}
