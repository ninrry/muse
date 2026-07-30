package luzzr.muse.ui.state

import luzzr.muse.R
import luzzr.muse.core.log.MuseLog
import luzzr.muse.domain.lyrics.LrcParser
import luzzr.muse.domain.lyrics.LrcSerializer
import luzzr.muse.domain.lyrics.LyricsFileReadResult
import luzzr.muse.domain.lyrics.LyricsFileReader
import luzzr.muse.domain.lyrics.LyricsTimeline
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 保留兼容；校正不再强制夹紧到此范围 */
@Deprecated("No hard clamp; kept for binary compatibility")
const val LYRIC_OFFSET_MAX_MS = 60 * 60 * 1000L

@Singleton
class LyricsStateHolder @Inject constructor(
    private val lyricsRepository: LyricsRepository,
    private val fetchLyricsUseCase: FetchLyricsUseCase,
    private val restoreLyricsCacheUseCase: RestoreLyricsCacheUseCase,
    private val clearLyricsCacheUseCase: ClearLyricsCacheUseCase,
    private val textNormalizer: TextNormalizer,
    private val metadataFileWriter: MetadataFileWriter,
    private val lyricsFileReader: LyricsFileReader
) : PlayerLyricsController {

    @Volatile
    private var currentSong: Song? = null

    private val _lyrics = MutableStateFlow<List<LrcLine>>(emptyList())
    override val lyrics: StateFlow<List<LrcLine>> = _lyrics.asStateFlow()
    @Volatile
    private var lyricsTimeline = LyricsTimeline(emptyList())

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

    private var bindJob: kotlinx.coroutines.Job? = null
    private var offsetPersistJob: kotlinx.coroutines.Job? = null
    @Volatile
    private var loadGeneration = 0L

    override fun bind(scope: CoroutineScope, progressFlow: StateFlow<Long>) {
        bindJob?.cancel()
        bindJob = scope.launch {
            kotlinx.coroutines.flow.combine(progressFlow, _lyricsOffsetMs) { progressMs, offsetMs ->
                progressMs to offsetMs
            }.collect { (progressMs, offsetMs) ->
                _positionMs.value = progressMs
                val lines = _lyrics.value
                if (lines.isEmpty()) {
                    if (_currentLyricLine.value != -1) _currentLyricLine.value = -1
                    return@collect
                }
                val lineIndex = lyricsTimeline.indexAt(progressMs, offsetMs)
                if (_currentLyricLine.value != lineIndex) {
                    _currentLyricLine.value = lineIndex
                }
            }
        }
    }

    override suspend fun loadLyrics(song: Song) {
        val generation = beginLoad()
        clearState()
        currentSong = song
        _lyricsLoading.value = true
        val initialOffset = lyricsRepository.loadLyricsOffset(song.id)
        if (!isCurrentLoad(generation)) return
        _lyricsOffsetMs.value = initialOffset
        try {
            val localLyrics = fetchLyricsUseCase.findLocal(song)
            if (!isCurrentLoad(generation)) return
            if (localLyrics != null &&
                (localLyrics.syncedLines.isNotEmpty() || !localLyrics.plainText.isNullOrBlank())
            ) {
                val rawLrc = localLyrics.rawSyncedLyrics ?: LrcSerializer.serialize(localLyrics.syncedLines)
                lyricsRepository.saveLyrics(
                    song.id,
                    rawLrc.takeIf { localLyrics.syncedLines.isNotEmpty() },
                    localLyrics.plainText
                )
                if (!isCurrentLoad(generation)) return
                if (localLyrics.syncedLines.isNotEmpty()) {
                    updateTimeline(localLyrics.syncedLines)
                    _lyricsError.value = null
                } else {
                    updateTimeline(emptyList())
                    _lyricsError.value = UiText.Resource(R.string.player_lyrics_plain)
                }
                _lyricsLoading.value = false
                return
            }

            val dbLyrics = lyricsRepository.loadLyrics(song.id)
            if (!isCurrentLoad(generation)) return
            if (dbLyrics != null) {
                val (syncedLyrics, plainText) = dbLyrics
                if (!syncedLyrics.isNullOrBlank()) {
                    val simplified = textNormalizer.toSimplified(syncedLyrics)
                    val simplifiedPlain = plainText?.let { textNormalizer.toSimplified(it) }
                    val parsed = LyricsTimeline(LrcParser.parse(simplified)).lines
                    if (parsed.isNotEmpty()) {
                        if (!isCurrentLoad(generation)) return
                        updateTimeline(parsed)
                        _lyricsLoading.value = false
                        _lyricsError.value = null
                        val dbOffset = lyricsRepository.loadLyricsOffset(song.id)
                        if (!isCurrentLoad(generation)) return
                        _lyricsOffsetMs.value = dbOffset
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
                    updateTimeline(emptyList())
                    _lyricsLoading.value = false
                    _lyricsError.value = UiText.Resource(R.string.player_lyrics_plain)
                    val dbOffset = lyricsRepository.loadLyricsOffset(song.id)
                    if (!isCurrentLoad(generation)) return
                    _lyricsOffsetMs.value = dbOffset
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            MuseLog.w("LyricsStateHolder", "DB lyrics lookup failed; falling back to network", e)
        }

        fetchLyrics(song, generation)
    }

    suspend fun fetchLyrics(song: Song) {
        val generation = beginLoad()
        clearState()
        currentSong = song
        fetchLyrics(song, generation)
    }

    private suspend fun fetchLyrics(song: Song, generation: Long) {
        if (!isCurrentLoad(generation)) return
        _lyricsLoading.value = true
        _lyricsError.value = null
        try {
            val result = fetchLyricsUseCase(song.id, song.title, song.artist, song.album)
            if (!isCurrentLoad(generation)) return
            if (result != null && (result.syncedLines.isNotEmpty() || !result.plainText.isNullOrBlank())) {
                val rawLrc = result.rawSyncedLyrics ?: LrcSerializer.serialize(result.syncedLines)
                lyricsRepository.saveLyrics(song.id, rawLrc.takeIf { it.isNotBlank() }, result.plainText)
                if (!isCurrentLoad(generation)) return
                val fetchedOffset = lyricsRepository.loadLyricsOffset(song.id)
                if (!isCurrentLoad(generation)) return
                _lyricsOffsetMs.value = fetchedOffset

                if (result.syncedLines.isNotEmpty()) {
                    updateTimeline(result.syncedLines)
                    _lyricsError.value = null
                } else {
                    updateTimeline(emptyList())
                    _lyricsError.value = UiText.Resource(R.string.player_lyrics_plain)
                }
            } else {
                updateTimeline(emptyList())
                _lyricsError.value = UiText.Resource(R.string.player_lyrics_not_found)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!isCurrentLoad(generation)) return
            MuseLog.w("LyricsStateHolder", "Lyrics fetch failed", e)
            updateTimeline(emptyList())
            _lyricsError.value = UiText.Resource(R.string.player_lyrics_error)
        } finally {
            if (isCurrentLoad(generation)) _lyricsLoading.value = false
        }
    }

    fun trackLineProgress(lines: List<LrcLine>, progressMs: Long, offsetMs: Long) {
        if (lines.isEmpty()) {
            if (_currentLyricLine.value != -1) _currentLyricLine.value = -1
            return
        }
        _positionMs.value = progressMs
        val lineIndex = LyricsTimeline(lines).indexAt(progressMs, offsetMs)
        if (_currentLyricLine.value != lineIndex) {
            _currentLyricLine.value = lineIndex
        }
    }

    override fun resetLyrics(scope: CoroutineScope, song: Song) {
        scope.launch {
            updateTimeline(emptyList())
            _currentLyricLine.value = -1
            _lyricsError.value = null
            _lyricsLoading.value = true
            lyricsRepository.deleteLyrics(song.id)
            fetchLyricsUseCase.clearCache(song.id)
            fetchLyrics(song)
        }
    }

    override fun adjustLyricsOffset(scope: CoroutineScope, songId: Long, deltaMs: Long) {
        if (_lyrics.value.isEmpty()) return
        // 无硬限制：仅即时更新 UI，DB 防抖写入，不写文件
        val newOffset = _lyricsOffsetMs.value + deltaMs
        _lyricsOffsetMs.value = newOffset
        scheduleOffsetPersist(scope, songId, newOffset)
    }

    override fun saveLyricsOffset(scope: CoroutineScope, songId: Long, offsetMs: Long, bakeToFile: Boolean) {
        if (_lyrics.value.isEmpty()) return
        _lyricsOffsetMs.value = offsetMs
        scope.launch {
            lyricsRepository.saveLyricsOffset(songId, offsetMs)
        }
        if (bakeToFile) {
            currentSong?.let { bakeCorrectionToSong(scope, it) }
        }
    }

    override fun resetLyricsOffset(scope: CoroutineScope, songId: Long) {
        _lyricsOffsetMs.value = 0L
        offsetPersistJob?.cancel()
        scope.launch {
            lyricsRepository.saveLyricsOffset(songId, 0L)
        }
        currentSong?.let { song ->
            scope.launch { metadataFileWriter.clearLyrics(song) }
        }
    }

    override fun commitLyricsOffset(scope: CoroutineScope, song: Song) {
        val offset = _lyricsOffsetMs.value
        scope.launch {
            lyricsRepository.saveLyricsOffset(song.id, offset)
        }
        bakeCorrectionToSong(scope, song)
    }

    override suspend fun searchLyricsCandidates(song: Song): List<LyricsResult> {
        return fetchLyricsUseCase.searchCandidates(song.title, song.artist, song.album)
    }

    override suspend fun applyLyricsResult(song: Song, result: LyricsResult) {
        val rawLrc = result.rawSyncedLyrics ?: LrcSerializer.serialize(result.syncedLines)
        lyricsRepository.saveLyrics(
            song.id,
            rawLrc.takeIf { result.syncedLines.isNotEmpty() },
            result.plainText
        )
        fetchLyricsUseCase.restore(song.id, result)
        if (result.syncedLines.isNotEmpty()) {
            updateTimeline(result.syncedLines)
            _lyricsError.value = null
        } else {
            updateTimeline(emptyList())
            _lyricsError.value = UiText.Resource(R.string.player_lyrics_plain)
        }
        _lyricsLoading.value = false
        _currentLyricLine.value = lyricsTimeline.indexAt(
            _positionMs.value,
            _lyricsOffsetMs.value
        )
        currentSong = song
    }

    override suspend fun applyLyricsFile(song: Song, uri: String): LyricsFileApplyResult {
        val content = when (val readResult = lyricsFileReader.read(uri)) {
            is LyricsFileReadResult.Success -> readResult.content
            LyricsFileReadResult.TooLarge -> return LyricsFileApplyResult.TOO_LARGE
            LyricsFileReadResult.Unreadable -> return LyricsFileApplyResult.UNREADABLE
        }
        val normalized = textNormalizer.toSimplified(content)
        val parsed = LyricsTimeline(LrcParser.parse(normalized)).lines
        if (parsed.isEmpty()) return LyricsFileApplyResult.INVALID

        applyLyricsResult(
            song = song,
            result = LyricsResult(
                id = null,
                trackName = song.title,
                artistName = song.artist,
                albumName = song.album,
                duration = song.duration / 1000.0,
                syncedLines = parsed,
                plainText = null,
                rawSyncedLyrics = normalized,
                source = "local"
            )
        )
        return LyricsFileApplyResult.APPLIED
    }

    private fun scheduleOffsetPersist(scope: CoroutineScope, songId: Long, offsetMs: Long) {
        offsetPersistJob?.cancel()
        offsetPersistJob = scope.launch {
            kotlinx.coroutines.delay(350)
            lyricsRepository.saveLyricsOffset(songId, offsetMs)
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
        // Shift line and word timestamps together. Serializing the original
        // timeline with an offset preserves enhanced karaoke data instead of
        // silently writing only the line timestamps back to the file.
        val lrc = LrcSerializer.serialize(lines, offsetMs = offset)
        scope.launch {
            metadataFileWriter.writeLyrics(song, lrc)
        }
    }

    override fun clear() {
        beginLoad()
        clearState()
    }

    private fun beginLoad(): Long {
        loadGeneration += 1L
        return loadGeneration
    }

    private fun isCurrentLoad(generation: Long): Boolean = loadGeneration == generation

    private fun updateTimeline(lines: List<LrcLine>) {
        lyricsTimeline = LyricsTimeline(lines)
        _lyrics.value = lyricsTimeline.lines
    }

    private fun clearState() {
        updateTimeline(emptyList())
        _currentLyricLine.value = -1
        _positionMs.value = 0L
        _lyricsError.value = null
        _lyricsOffsetMs.value = 0L
        _lyricsLoading.value = false
        currentSong = null
    }
}
