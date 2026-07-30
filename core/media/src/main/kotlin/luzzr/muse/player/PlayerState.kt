package luzzr.muse.player

import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import luzzr.muse.core.log.MuseLog
import luzzr.muse.domain.model.MediaClassifier
import luzzr.muse.domain.model.Song
import luzzr.muse.media.PlaybackController
import luzzr.muse.media.PlaybackRepeatMode
import luzzr.muse.media.PlaybackState
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerState @Inject constructor(
    val sessionPersistence: SessionPersistenceManager
) : PlaybackController {

    override val sleepTimer = SleepTimer()

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

    // --- Player reference (set by MusicService) ---

    @Volatile
    private var player: ExoPlayer? = null

    private val pendingOperations = ConcurrentLinkedQueue<() -> Unit>()

    fun initSession(prefs: android.content.SharedPreferences) {
        sessionPersistence.initSessionPrefs(prefs)
        val shuffle = sessionPersistence.shuffleModeOverride
        val repeat = sessionPersistence.repeatModeOverride
        _shuffleMode.value = shuffle
        _repeatMode.value = repeat
        _state.update {
            it.copy(
                shuffleEnabled = shuffle,
                repeatMode = repeat.toPlaybackRepeatMode()
            )
        }
    }

    fun attachPlayer(exoPlayer: ExoPlayer) {
        player = exoPlayer
        exoPlayer.repeatMode = _repeatMode.value
        exoPlayer.shuffleModeEnabled = _shuffleMode.value
        MuseLog.d("PlayerState", "attachPlayer: attached. pendingOps=${pendingOperations.size}")
        while (pendingOperations.isNotEmpty()) {
            pendingOperations.poll()?.invoke()
        }
    }

    fun detachPlayer() {
        MuseLog.d("PlayerState", "detachPlayer: player detached (service destroyed)")
        player = null
        _isPlaying.value = false
        _progress.value = 0L
        _state.update { it.copy(isPlaying = false, positionMs = 0L) }
    }

    fun clearPendingOperations() {
        if (pendingOperations.isNotEmpty()) {
            MuseLog.w("PlayerState", "clearPendingOperations: cleared ${pendingOperations.size} ops")
        }
        pendingOperations.clear()
    }

    // ============================================================
    // PlaybackController interface
    // ============================================================

    private fun Song.toMediaItem(includeArtworkData: Boolean = false): MediaItem {
        return MediaItem.Builder()
            .setMediaId(id.toString())
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setDisplayTitle(title)
                    .setArtist(artist)
                    .setSubtitle(artist)
                    .setAlbumTitle(album)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .setIsPlayable(true)
                    .setIsBrowsable(false)
                    .apply {
                        artworkUri
                            ?.toUri()
                            ?.let(::setArtworkUri)
                        if (includeArtworkData) {
                            readLocalArtworkBytes(artworkUri)?.let { bytes ->
                                setArtworkData(bytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                            }
                        }
                    }
                    .build()
            )
            .build()
    }

    /**
     * HyperOS and Android 13+ can consume the media session metadata directly,
     * without being able to open the app-private file URI. Include the current
     * cover bytes when they are a small local cache file; keep the URI as a
     * fallback for content providers and larger artwork.
     */
    private fun readLocalArtworkBytes(artwork: String?): ByteArray? {
        val parsed = artwork?.toUri() ?: return null
        if (parsed.scheme?.lowercase() != "file") return null
        val path = parsed.path ?: return null
        val file = File(path)
        if (!file.isFile || file.length() <= 0L || file.length() > MAX_ARTWORK_DATA_BYTES) return null
        return try {
            file.readBytes()
        } catch (_: SecurityException) {
            null
        } catch (_: java.io.IOException) {
            null
        }
    }

    private companion object {
        const val MAX_ARTWORK_DATA_BYTES = 512 * 1024L
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
            MuseLog.d("PlayerState", "playSongs: player=null, queue op (songs=${playableSongs.size})")
            pendingOperations.add { playSongs(songs, startIndex, enableShuffle) }
            return
        }

        val mediaItems = playableSongs.mapIndexed { index, song ->
            song.toMediaItem(includeArtworkData = index == safeStartIndex)
        }

        val targetSong = playableSongs.getOrNull(safeStartIndex)
        val startPos = if (targetSong != null && MediaClassifier.isAudiobook(targetSong)) {
            sessionPersistence.getSavedSongProgress(targetSong.id)
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
            MuseLog.d("PlayerState", "togglePlayPause: player=null, queue op")
            pendingOperations.add { togglePlayPause() }
            return
        }
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

            val mediaItems = shuffledList.mapIndexed { index, song ->
                song.toMediaItem(includeArtworkData = index == 0)
            }
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

    internal fun updateSongInPlaylist(index: Int, updatedSong: Song) {
        val list = _currentPlaylist.value.toMutableList()
        if (index in list.indices) {
            list[index] = updatedSong
            _currentPlaylist.value = list
            _state.update { it.copy(playlist = list) }
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
        // 节流：位置变化不足 40ms 且未回跳时不推送，降低 20Hz 全链路压力
        val prev = _progress.value
        if (progress == prev) return
        if (progress > prev && progress - prev < 40L) return
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

    override fun refreshCurrentSong(song: Song) {
        val refreshedSong = song.withUsableLocalArtwork()
        val playlist = _currentPlaylist.value
        val updatedPlaylist = playlist.map { current ->
            if (current.id == refreshedSong.id) refreshedSong else current
        }

        if (updatedPlaylist != playlist) {
            _currentPlaylist.value = updatedPlaylist
            originalPlaylist = originalPlaylist.map { current ->
                if (current.id == refreshedSong.id) refreshedSong else current
            }
            _state.update { it.copy(playlist = updatedPlaylist) }
        }

        if (_currentSong.value?.id == refreshedSong.id) {
            updateCurrentSong(refreshedSong)
        }

        val p = player ?: return
        if (p.mediaItemCount == 0) return
        updatedPlaylist.forEachIndexed { index, current ->
            if (current.id == refreshedSong.id && index < p.mediaItemCount) {
                p.replaceMediaItem(index, refreshedSong.toMediaItem(includeArtworkData = true))
            }
        }
    }

    // --- Session persistence ---

    internal fun saveSession() {
        val currentList = _currentPlaylist.value
        val idx = player?.currentMediaItemIndex ?: -1
        val pos = player?.currentPosition ?: 0L
        sessionPersistence.saveSession(currentList, idx, pos, _shuffleMode.value, _repeatMode.value)
    }

    override fun hasSavedSession(): Boolean = sessionPersistence.hasSavedSession()

    override fun getSavedPlaylistIds(): List<Long> = sessionPersistence.getSavedPlaylistIds()

    override fun getSavedPlaybackInfo(): Pair<Int, Long> = sessionPersistence.getSavedPlaybackInfo()

    override fun getSavedShuffleMode(): Boolean = sessionPersistence.getSavedShuffleMode()

    override fun getSavedRepeatMode(): PlaybackRepeatMode = sessionPersistence.getSavedRepeatMode()

    fun saveSongProgress(songId: Long, progress: Long) {
        sessionPersistence.saveSongProgress(songId, progress)
    }

    override fun getSavedSongProgress(songId: Long): Long = sessionPersistence.getSavedSongProgress(songId)

    fun updateSongLastPlayedTime(songId: Long) {
        sessionPersistence.updateSongLastPlayedTime(songId)
    }

    override fun getSongLastPlayedTime(songId: Long): Long = sessionPersistence.getSongLastPlayedTime(songId)

    override fun clearSavedSession() {
        sessionPersistence.clearSavedSession()
    }
}
