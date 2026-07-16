package luzzr.muse.ui.state

import luzzr.muse.R
import luzzr.muse.core.log.MuseLog
import luzzr.muse.domain.lyrics.LrcParser
import luzzr.muse.domain.model.LrcLine
import luzzr.muse.domain.model.LyricsResult
import luzzr.muse.domain.model.Song
import luzzr.muse.domain.repository.LyricsRepository
import luzzr.muse.data.tag.MetadataFileWriter
import luzzr.muse.domain.text.TextNormalizer
import luzzr.muse.domain.usecase.ClearLyricsCacheUseCase
import luzzr.muse.domain.usecase.FetchLyricsUseCase
import luzzr.muse.domain.usecase.RestoreLyricsCacheUseCase
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

const val LYRIC_OFFSET_MAX_MS = 10000L

@Singleton
class LyricsStateHolder @Inject constructor(
    private val lyricsRepository: LyricsRepository,
    private val fetchLyricsUseCase: FetchLyricsUseCase,
    private val restoreLyricsCacheUseCase: RestoreLyricsCacheUseCase,
    private val clearLyricsCacheUseCase: ClearLyricsCacheUseCase,
    private val textNormalizer: TextNormalizer,
    private val metadataFileWriter: MetadataFileWriter
) : PlayerLyricsController {

    @Volatile
    private var currentSong: Song? = null

    private val _lyrics = MutableStateFlow<List<LrcLine>>(emptyList())
    override val lyrics: StateFlow<List<LrcLine>> = _lyrics.asStateFlow()

    private val _currentLyricLine = MutableStateFlow(-1)
    override val currentLyricLine: StateFlow<Int> = _currentLyricLine.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    override val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _lyricsLoading = MutableStateFlow(false)
    override val lyricsLoading: StateFlow<Boolean> = _lyricsLoading.asStateFlow()

    private val _lyricsError = MutableStateFlow<UiText?>(null)
    override val lyricsError: StateFlow<UiText?> = _lyricsError.asStateFlow()

    private val _lyricsOffsetMs = MutableStateFlow(0L)
    override val lyricsOffsetMs: StateFlow<Long> = _lyricsOffsetMs.asStateFlow()

    override fun bind(scope: CoroutineScope, progressFlow: StateFlow<Long>) {
        scope.launch {
            kotlinx.coroutines.flow.combine(progressFlow, _lyricsOffsetMs) { progressMs, offsetMs ->
                progressMs to offsetMs
            }.collect { (progressMs, offsetMs) ->
                _positionMs.value = progressMs
                val lines = _lyrics.value
                if (lines.isEmpty()) {
                    if (_currentLyricLine.value != -1) _currentLyricLine.value = -1
                    return@collect
                }
                val adjustedPos = (progressMs + offsetMs).coerceAtLeast(0L)
                val lineIndex = LrcParser.getLineIndex(lines, adjustedPos)
                if (_currentLyricLine.value != lineIndex) {
                    _currentLyricLine.value = lineIndex
                }
            }
        }
    }

    override suspend fun loadLyrics(song: Song) {
        clear()
        currentSong = song
        _lyricsLoading.value = true
        _lyricsOffsetMs.value = lyricsRepository.loadLyricsOffset(song.id)
        try {
            val dbLyrics = lyricsRepository.loadLyrics(song.id)
            if (dbLyrics != null) {
                val (syncedLyrics, plainText) = dbLyrics
                if (!syncedLyrics.isNullOrBlank()) {
                    val simplified = textNormalizer.toSimplified(syncedLyrics)
                    val simplifiedPlain = plainText?.let { textNormalizer.toSimplified(it) }
                    val parsed = LrcParser.parse(simplified)
                    if (parsed.isNotEmpty()) {
                        _lyrics.value = parsed
                        _lyricsLoading.value = false
                        _lyricsError.value = null
                        _lyricsOffsetMs.value = lyricsRepository.loadLyricsOffset(song.id)
                        restoreLyricsCacheUseCase(
                            song.id,
                            LyricsResult(
                                id = null,
                                trackName = song.title,
                                artistName = song.artist,
                                albumName = song.album,
                                duration = song.duration / 1000.0,
                                syncedLines = parsed,
                                plainText = simplifiedPlain,
                                rawSyncedLyrics = simplified
                            )
                        )
                        return
                    }
                } else if (!plainText.isNullOrBlank()) {
                    val simplifiedPlain = textNormalizer.toSimplified(plainText)
                    _lyrics.value = emptyList()
                    _lyricsLoading.value = false
                    _lyricsError.value = UiText.Resource(R.string.player_lyrics_plain)
                    _lyricsOffsetMs.value = lyricsRepository.loadLyricsOffset(song.id)
                    restoreLyricsCacheUseCase(
                        song.id,
                        LyricsResult(
                            id = null,
                            trackName = song.title,
                            artistName = song.artist,
                            albumName = song.album,
                            duration = song.duration / 1000.0,
                            syncedLines = emptyList(),
                            plainText = simplifiedPlain
                        )
                    )
                    return
                }
            }
        } catch (e: Exception) {
            MuseLog.w("LyricsStateHolder", "DB lyrics lookup failed; falling back to network", e)
        }

        fetchLyrics(song)
    }

    suspend fun fetchLyrics(song: Song) {
        _lyricsLoading.value = true
        _lyricsError.value = null
        try {
            val result = fetchLyricsUseCase(song.id, song.title, song.artist, song.album)
            if (result != null && (result.syncedLines.isNotEmpty() || !result.plainText.isNullOrBlank())) {
                val rawLrc = result.rawSyncedLyrics ?: result.syncedLines.joinToString("\n") { line ->
                    val mins = line.timestamp / 60000
                    val secs = (line.timestamp % 60000) / 1000
                    val millis = line.timestamp % 1000
                    "[%02d:%02d.%03d]%s".format(mins, secs, millis, line.text)
                }
                lyricsRepository.saveLyrics(song.id, rawLrc.takeIf { it.isNotBlank() }, result.plainText)
                _lyricsOffsetMs.value = lyricsRepository.loadLyricsOffset(song.id)

                if (result.syncedLines.isNotEmpty()) {
                    _lyrics.value = result.syncedLines
                    _lyricsError.value = null
                } else {
                    _lyrics.value = emptyList()
                    _lyricsError.value = UiText.Resource(R.string.player_lyrics_plain)
                }
            } else {
                _lyrics.value = emptyList()
                _lyricsError.value = UiText.Resource(R.string.player_lyrics_not_found)
            }
        } catch (e: Exception) {
            MuseLog.w("LyricsStateHolder", "Lyrics fetch failed", e)
            _lyrics.value = emptyList()
            _lyricsError.value = UiText.Resource(R.string.player_lyrics_error)
        } finally {
            _lyricsLoading.value = false
        }
    }

    fun trackLineProgress(lines: List<LrcLine>, progressMs: Long, offsetMs: Long) {
        if (lines.isEmpty()) {
            if (_currentLyricLine.value != -1) _currentLyricLine.value = -1
            return
        }
        _positionMs.value = progressMs
        val adjustedPos = (progressMs + offsetMs).coerceAtLeast(0L)
        val lineIndex = LrcParser.getLineIndex(lines, adjustedPos)
        if (_currentLyricLine.value != lineIndex) {
            _currentLyricLine.value = lineIndex
        }
    }

    override fun resetLyrics(scope: CoroutineScope, song: Song) {
        scope.launch {
            _lyrics.value = emptyList()
            _currentLyricLine.value = -1
            _lyricsError.value = null
            _lyricsLoading.value = true
            lyricsRepository.deleteLyrics(song.id)
            clearLyricsCacheUseCase()
            fetchLyrics(song)
        }
    }

    override fun adjustLyricsOffset(scope: CoroutineScope, songId: Long, deltaMs: Long) {
        if (_lyrics.value.isEmpty()) return
        val newOffset = (_lyricsOffsetMs.value + deltaMs).coerceIn(-LYRIC_OFFSET_MAX_MS, LYRIC_OFFSET_MAX_MS)
        _lyricsOffsetMs.value = newOffset
        scope.launch {
            lyricsRepository.saveLyricsOffset(songId, newOffset)
        }
    }

    override fun saveLyricsOffset(scope: CoroutineScope, songId: Long, offsetMs: Long) {
        if (_lyrics.value.isEmpty()) return
        val clamped = offsetMs.coerceIn(-LYRIC_OFFSET_MAX_MS, LYRIC_OFFSET_MAX_MS)
        _lyricsOffsetMs.value = clamped
        scope.launch {
            lyricsRepository.saveLyricsOffset(songId, clamped)
        }
        currentSong?.let { bakeCorrectionToSong(scope, it) }
    }

    override fun resetLyricsOffset(scope: CoroutineScope, songId: Long) {
        _lyricsOffsetMs.value = 0L
        scope.launch {
            lyricsRepository.saveLyricsOffset(songId, 0L)
        }
        currentSong?.let { song ->
            scope.launch { metadataFileWriter.clearLyrics(song) }
        }
    }

    /**
     * Persist the currently displayed (offset-corrected) synced lyrics into the
     * audio file's LYRICS tag, so a manual calibration survives reinstalls and is
     * visible to other players.
     */
    private fun bakeCorrectionToSong(scope: CoroutineScope, song: Song) {
        val lines = _lyrics.value
        if (lines.isEmpty()) return
        val offset = _lyricsOffsetMs.value
        val corrected = if (offset == 0L) {
            lines
        } else {
            lines.map { it.copy(timestamp = (it.timestamp - offset).coerceAtLeast(0L)) }
        }
        val lrc = corrected.joinToString("\n") { line ->
            val mins = line.timestamp / 60000
            val secs = (line.timestamp % 60000) / 1000
            val millis = line.timestamp % 1000
            "[%02d:%02d.%03d]%s".format(mins, secs, millis, line.text)
        }
        scope.launch {
            metadataFileWriter.writeLyrics(song, lrc)
        }
    }

    override fun clear() {
        _lyrics.value = emptyList()
        _currentLyricLine.value = -1
        _positionMs.value = 0L
        _lyricsError.value = null
        _lyricsOffsetMs.value = 0L
    }
}
