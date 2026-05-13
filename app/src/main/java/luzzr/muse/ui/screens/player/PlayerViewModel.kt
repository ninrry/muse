package luzzr.muse.ui.screens.player

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import luzzr.muse.MuseApp
import luzzr.muse.data.model.Song
import luzzr.muse.data.network.LrcLine
import luzzr.muse.data.network.LrcParser
import luzzr.muse.data.network.LyricsFetcher
import luzzr.muse.data.network.MetadataResult
import luzzr.muse.data.repository.MusicRepository
import luzzr.muse.player.MusicService
import luzzr.muse.player.PlayerState
import luzzr.muse.player.SleepTimerMode

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val playerState: PlayerState = (application as MuseApp).playerState
    private val musicRepo = MusicRepository.getInstance(application)

    val currentSong: StateFlow<Song?> = playerState.currentSong
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val isPlaying: StateFlow<Boolean> = playerState.isPlaying
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val progress: StateFlow<Long> = playerState.progress
    val duration: StateFlow<Long> = playerState.duration
    val repeatMode: StateFlow<Int> = playerState.repeatMode
    val shuffleMode: StateFlow<Boolean> = playerState.shuffleMode
    val currentPlaylist: StateFlow<List<Song>> = playerState.currentPlaylist

    // --- Sleep Timer ---

    val sleepTimer = playerState.sleepTimer

    // --- Lyrics ---

    private val _lyrics = MutableStateFlow<List<LrcLine>>(emptyList())
    val lyrics: StateFlow<List<LrcLine>> = _lyrics.asStateFlow()

    private val _currentLyricLine = MutableStateFlow(-1)
    val currentLyricLine: StateFlow<Int> = _currentLyricLine.asStateFlow()

    /** Progress (0..1) within the current lyric line for karaoke fill effect. */
    private val _lineProgress = MutableStateFlow(0f)
    val lineProgress: StateFlow<Float> = _lineProgress.asStateFlow()

    private val _lyricsLoading = MutableStateFlow(false)
    val lyricsLoading: StateFlow<Boolean> = _lyricsLoading.asStateFlow()

    private val _lyricsError = MutableStateFlow<String?>(null)
    val lyricsError: StateFlow<String?> = _lyricsError.asStateFlow()

    /** Adjustable lyrics playback speed multiplier. 1.0f = normal. */
    private val _lyricsSpeed = MutableStateFlow(1.0f)
    val lyricsSpeed: StateFlow<Float> = _lyricsSpeed.asStateFlow()

    private val lyricsFetcher = LyricsFetcher.getInstance()

    init {
        // --- Session restoration: recover from process death ---
        // If ExoPlayer has no media items but we have a saved session,
        // restore it proactively. Covers MIUI/aggressive OEM killing.
        viewModelScope.launch {
            // Give MusicService a moment to restore on its own
            kotlinx.coroutines.delay(800)
            if (currentPlaylist.value.isEmpty() && playerState.hasSavedSession()) {
                android.util.Log.w("PlayerViewModel", "init: playlist empty, restoring saved session")
                restoreSessionFromPrefs()
            }
        }

        // Auto-fetch lyrics when current song changes
        viewModelScope.launch {
            playerState.currentSong.collect { song ->
                if (song != null) {
                    loadLyrics(song)
                    ensureArtwork(song)
                } else {
                    _lyrics.value = emptyList()
                    _currentLyricLine.value = -1
                    _lyricsError.value = null
                }
            }
        }

        // Track current lyric line + karaoke fill progress based on playback position
        // with adjustable lyrics speed multiplier
        viewModelScope.launch {
            playerState.progress.collect { progressMs ->
                val lines = _lyrics.value
                val speed = _lyricsSpeed.value
                if (lines.isNotEmpty() && speed > 0f) {
                    // Apply lyrics speed: scale playback position before line lookup
                    val adjustedPos = (progressMs * speed).toLong()
                    val lineIndex = LrcParser.getLineIndex(lines, adjustedPos)
                    _currentLyricLine.value = lineIndex
                    // Calculate progress within the current line for karaoke fill
                    if (lineIndex >= 0 && lineIndex < lines.size - 1) {
                        val currentLine = lines[lineIndex]
                        val nextLine = lines[lineIndex + 1]
                        val lineDuration = nextLine.timestamp - currentLine.timestamp
                        if (lineDuration > 0) {
                            _lineProgress.value = ((adjustedPos - currentLine.timestamp).toFloat() / lineDuration).coerceIn(0f, 1f)
                        } else {
                            _lineProgress.value = 1f
                        }
                    } else if (lineIndex >= 0 && lineIndex == lines.size - 1) {
                        // Last line: always fully filled
                        _lineProgress.value = 1f
                    } else {
                        _lineProgress.value = 0f
                    }
                }
            }
        }
    }

    /**
     * Load lyrics from DB cache first, then fall back to network fetch.
     * Persists network results to DB for future cold starts.
     */
    private suspend fun loadLyrics(song: Song) {
        // Phase 1: Try DB cache first
        try {
            val dbLyrics = withContext(Dispatchers.IO) {
                musicRepo.loadLyrics(song.id)
            }
            if (dbLyrics != null) {
                val (syncedLyrics, plainText) = dbLyrics
                if (!syncedLyrics.isNullOrBlank()) {
                    // Always convert Traditional→Simplified Chinese (DB may have pre-conversion data)
                    val simplified = luzzr.muse.data.network.MetadataResult.toSimplifiedText(syncedLyrics)
                    val parsed = LrcParser.parse(simplified)
                    if (parsed.isNotEmpty()) {
                        _lyrics.value = parsed
                        _lyricsLoading.value = false
                        _lyricsError.value = null
                        // Restore saved lyrics speed
                        _lyricsSpeed.value = musicRepo.loadLyricsSpeed(song.id)
                        // Restore to in-memory cache for fast access
                        lyricsFetcher.restoreToCache(song.id, luzzr.muse.data.network.LyricsResult(
                            id = null, trackName = song.title,
                            artistName = song.artist, albumName = song.album,
                            duration = song.duration / 1000.0,
                            syncedLines = parsed, plainText = plainText
                        ))
                        return
                    }
                }
            }
        } catch (_: Exception) { }

        // Phase 2: Fetch from network
        fetchLyrics(song)
    }

    /**
     * Generate a default cover for the song, replacing any existing artwork.
     * Generates, writes to internal storage, updates state, and persists to DB.
     */
    private suspend fun ensureArtwork(song: Song) {
        android.util.Log.d("PlayerViewModel", "ensureArtwork: generating default cover for song=${song.id}")
        musicRepo.generateDefaultCoverForSong(song)
    }

    /**
     * Reset lyrics for the current song: delete from DB cache, clear in-memory,
     * and re-fetch from network. Called when user taps the refresh button.
     */
    fun resetLyrics() {
        val song = currentSong.value ?: return
        viewModelScope.launch {
            _lyrics.value = emptyList()
            _currentLyricLine.value = -1
            _lyricsError.value = null
            _lyricsLoading.value = true
            // Clear DB cache
            withContext(Dispatchers.IO) {
                musicRepo.deleteLyrics(song.id)
            }
            // Clear in-memory cache
            lyricsFetcher.clearCache()
            // Re-fetch from network
            fetchLyrics(song)
        }
    }

    private suspend fun fetchLyrics(song: Song) {
        _lyricsLoading.value = true
        _lyricsError.value = null
        try {
            val result = lyricsFetcher.fetchSync(
                songId = song.id,
                title = song.title,
                artist = song.artist.takeIf { it != "Unknown Artist" },
                album = song.album.takeIf { it != "Unknown Album" }
            )
            if (result != null && result.syncedLines.isNotEmpty()) {
                _lyrics.value = result.syncedLines
                _lyricsError.value = null

                // Persist to DB for future cold starts
                viewModelScope.launch {
                    // Reconstruct raw LRC text from the synced lines for DB storage
                    val rawLrc = result.syncedLines.joinToString("\n") { line ->
                        val mins = line.timestamp / 60000
                        val secs = (line.timestamp % 60000) / 1000
                        val millis = line.timestamp % 1000
                        "[%02d:%02d.%03d]%s".format(mins, secs, millis, line.text)
                    }
                    musicRepo.saveLyrics(song.id, rawLrc, result.plainText)
                    // Load and apply saved lyrics speed after DB persist
                    _lyricsSpeed.value = musicRepo.loadLyricsSpeed(song.id)
                }
            } else {
                _lyrics.value = emptyList()
                _lyricsError.value = "未找到同步歌词"
            }
        } catch (e: Exception) {
            _lyrics.value = emptyList()
            _lyricsError.value = "歌词获取失败"
        } finally {
            _lyricsLoading.value = false
        }
    }

    /**
     * Adjust lyrics speed by adding a delta (e.g. ±0.05).
     * Clamped to [0.50, 2.00] range. Persisted immediately.
     */
    fun adjustLyricsSpeed(delta: Float) {
        val song = currentSong.value ?: return
        val newSpeed = (_lyricsSpeed.value + delta).coerceIn(0.50f, 2.00f)
        _lyricsSpeed.value = newSpeed
        viewModelScope.launch {
            musicRepo.saveLyricsSpeed(song.id, newSpeed)
        }
    }

    /** Reset lyrics speed to 1.0x (normal). */
    fun resetLyricsSpeed() {
        val song = currentSong.value ?: return
        _lyricsSpeed.value = 1.0f
        viewModelScope.launch {
            musicRepo.saveLyricsSpeed(song.id, 1.0f)
        }
    }

    // --- Playback Controls ---

    /**
     * Restore the last playing session from SharedPreferences.
     * Used as a fallback when MusicService doesn't restore on its own
     * (e.g., after aggressive process killing on MIUI).
     */
    private suspend fun restoreSessionFromPrefs() {
        val ids = playerState.getSavedPlaylistIds()
        if (ids.isEmpty()) return
        val (savedIndex, savedPos) = playerState.getSavedPlaybackInfo()
        android.util.Log.w("PlayerViewModel", "restoreSessionFromPrefs: ids=${ids.size} idx=$savedIndex pos=$savedPos")
        try {
            val allSongs = withContext(Dispatchers.IO) {
                val songs = musicRepo.songs.value
                if (songs.isEmpty()) musicRepo.loadFromDatabase() else songs
            }
            val savedSongs = ids.mapNotNull { id -> allSongs.find { it.id == id } }
            if (savedSongs.isEmpty()) {
                android.util.Log.w("PlayerViewModel", "restoreSessionFromPrefs: no matching songs, clearing")
                playerState.clearSavedSession()
                return
            }
            // Ensure service is started
            val ctx = getApplication<Application>()
            try {
                ctx.startForegroundService(Intent(ctx, MusicService::class.java))
            } catch (_: Exception) {}
            // Small delay for service onCreate to complete
            kotlinx.coroutines.delay(200)
            playerState.playSongs(savedSongs, savedIndex.coerceIn(0, savedSongs.size - 1))
            if (savedPos > 0) {
                kotlinx.coroutines.delay(100)
                playerState.seekTo(savedPos)
            }
            // Pause at restored position; user taps to resume
            kotlinx.coroutines.delay(200)
            if (playerState.isPlaying.value) {
                playerState.togglePlayPause() // pause
            }
            android.util.Log.w("PlayerViewModel", "restoreSessionFromPrefs: done, ${savedSongs.size} songs restored")
        } catch (e: Exception) {
            android.util.Log.e("PlayerViewModel", "restoreSessionFromPrefs failed", e)
        }
    }

    fun togglePlayPause() {
        // Always ensure service is started; controller/player may be null after task removal.
        val ctx = getApplication<Application>()
        android.util.Log.d("PlayerViewModel", "togglePlayPause: request")
        try { ctx.startForegroundService(Intent(ctx, MusicService::class.java)) } catch (_: Exception) {}
        // If playlist empty but session saved, restore first
        if (currentPlaylist.value.isEmpty() && playerState.hasSavedSession()) {
            android.util.Log.w("PlayerViewModel", "togglePlayPause: playlist empty, triggering restore")
            viewModelScope.launch { restoreSessionFromPrefs() }
            return
        }
        playerState.togglePlayPause()
    }

    fun skipToNext() {
        val ctx = getApplication<Application>()
        try { ctx.startForegroundService(Intent(ctx, MusicService::class.java)) } catch (_: Exception) {}
        if (currentPlaylist.value.isEmpty() && playerState.hasSavedSession()) {
            viewModelScope.launch { restoreSessionFromPrefs() }; return
        }
        playerState.skipToNext()
    }

    fun skipToPrevious() {
        val ctx = getApplication<Application>()
        try { ctx.startForegroundService(Intent(ctx, MusicService::class.java)) } catch (_: Exception) {}
        if (currentPlaylist.value.isEmpty() && playerState.hasSavedSession()) {
            viewModelScope.launch { restoreSessionFromPrefs() }; return
        }
        playerState.skipToPrevious()
    }

    fun seekTo(position: Long) {
        val ctx = getApplication<Application>()
        try { ctx.startForegroundService(Intent(ctx, MusicService::class.java)) } catch (_: Exception) {}
        if (currentPlaylist.value.isEmpty() && playerState.hasSavedSession()) {
            viewModelScope.launch { restoreSessionFromPrefs() }; return
        }
        playerState.seekTo(position)
    }

    fun setRepeatMode(mode: Int) { playerState.setRepeatMode(mode) }
    fun toggleShuffle() { playerState.toggleShuffle() }

    fun cycleRepeatMode() {
        val next = when (repeatMode.value) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        setRepeatMode(next)
    }

    fun playSongAtIndex(index: Int) {
        val list = currentPlaylist.value
        if (index in list.indices) {
            val ctx = getApplication<Application>()
            ctx.startForegroundService(Intent(ctx, MusicService::class.java))
            playerState.playSongs(list, index)
        }
    }

    // --- Sleep Timer ---

    fun startSleepTimer(mode: SleepTimerMode) {
        val currentDuration = duration.value
        val trackRemaining = if (currentDuration > 0 && progress.value < currentDuration) {
            currentDuration - progress.value
        } else null
        sleepTimer.start(mode, trackRemaining)
    }

    fun stopSleepTimer() {
        sleepTimer.stop()
    }
}
