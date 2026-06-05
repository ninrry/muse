package luzzr.muse.ui.state

import luzzr.muse.R
import luzzr.muse.core.log.MuseLog
import luzzr.muse.domain.lyrics.LrcParser
import luzzr.muse.domain.model.LrcLine
import luzzr.muse.domain.model.LyricsResult
import luzzr.muse.domain.model.Song
import luzzr.muse.domain.repository.LyricsRepository
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
    private val textNormalizer: TextNormalizer
) : PlayerLyricsController {

    private val _lyrics = MutableStateFlow<List<LrcLine>>(emptyList())
    override val lyrics: StateFlow<List<LrcLine>> = _lyrics.asStateFlow()

    private val _currentLyricLine = MutableStateFlow(-1)
    override val currentLyricLine: StateFlow<Int> = _currentLyricLine.asStateFlow()

    private val _lineProgress = MutableStateFlow(0f)
    override val lineProgress: StateFlow<Float> = _lineProgress.asStateFlow()

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
                trackLineProgress(_lyrics.value, progressMs, offsetMs)
            }
        }
    }

    override suspend fun loadLyrics(song: Song) {
        clear()
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
        if (lines.isEmpty()) return
        val adjustedPos = (progressMs + offsetMs).coerceAtLeast(0L)
        val lineIndex = LrcParser.getLineIndex(lines, adjustedPos)
        _currentLyricLine.value = lineIndex
        if (lineIndex >= 0 && lineIndex < lines.size - 1) {
            val currentLine = lines[lineIndex]
            val nextLine = lines[lineIndex + 1]
            val lineDuration = nextLine.timestamp - currentLine.timestamp
            if (lineDuration > 0) {
                _lineProgress.value =
                    ((adjustedPos - currentLine.timestamp).toFloat() / lineDuration).coerceIn(0f, 1f)
            } else {
                _lineProgress.value = 1f
            }
        } else if (lineIndex >= 0 && lineIndex == lines.size - 1) {
            _lineProgress.value = 1f
        } else {
            _lineProgress.value = 0f
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
        val currentList = _lyrics.value
        if (currentList.isEmpty()) return

        val updatedList = currentList.map { line ->
            line.copy(timestamp = (line.timestamp + deltaMs).coerceAtLeast(0L))
        }
        _lyrics.value = updatedList

        scope.launch {
            val existing = lyricsRepository.loadLyrics(songId)
            val plainText = existing?.second
            val rawLrc = updatedList.toLrcString()
            lyricsRepository.saveLyrics(songId, rawLrc.takeIf { it.isNotBlank() }, plainText)
            lyricsRepository.saveLyricsOffset(songId, 0L)
            _lyricsOffsetMs.value = 0L
        }
    }

    override fun saveLyricsOffset(scope: CoroutineScope, songId: Long, offsetMs: Long) {
        if (offsetMs == 0L) return
        val currentList = _lyrics.value
        if (currentList.isEmpty()) return

        val updatedList = currentList.map { line ->
            line.copy(timestamp = (line.timestamp + offsetMs).coerceAtLeast(0L))
        }
        _lyrics.value = updatedList

        scope.launch {
            val existing = lyricsRepository.loadLyrics(songId)
            val plainText = existing?.second
            val rawLrc = updatedList.toLrcString()
            lyricsRepository.saveLyrics(songId, rawLrc.takeIf { it.isNotBlank() }, plainText)
            lyricsRepository.saveLyricsOffset(songId, 0L)
            _lyricsOffsetMs.value = 0L
        }
    }

    override fun resetLyricsOffset(scope: CoroutineScope, songId: Long) {
        _lyricsOffsetMs.value = 0L
        scope.launch {
            lyricsRepository.saveLyricsOffset(songId, 0L)
        }
    }

    override fun clear() {
        _lyrics.value = emptyList()
        _currentLyricLine.value = -1
        _lyricsError.value = null
    }

    private fun List<LrcLine>.toLrcString(): String {
        return joinToString("\n") { line ->
            val timestamp = line.timestamp.coerceAtLeast(0L)
            val mins = timestamp / 60000
            val secs = (timestamp % 60000) / 1000
            val millis = timestamp % 1000
            "[%02d:%02d.%03d]%s".format(mins, secs, millis, line.text)
        }
    }
}
