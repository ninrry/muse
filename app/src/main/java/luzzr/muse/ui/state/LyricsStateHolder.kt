package luzzr.muse.ui.state

import luzzr.muse.core.log.MuseLog
import luzzr.muse.data.model.Song
import luzzr.muse.data.network.LrcLine
import luzzr.muse.data.network.LrcParser
import luzzr.muse.data.network.LyricsFetcher
import luzzr.muse.data.network.LyricsResult
import luzzr.muse.data.network.SearchMatch
import luzzr.muse.data.network.toSimplifiedText
import luzzr.muse.data.repository.MusicRepositoryFacade
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
    private val musicRepo: MusicRepositoryFacade,
    private val lyricsFetcher: LyricsFetcher
) {

    private val _lyrics = MutableStateFlow<List<LrcLine>>(emptyList())
    val lyrics: StateFlow<List<LrcLine>> = _lyrics.asStateFlow()

    private val _currentLyricLine = MutableStateFlow(-1)
    val currentLyricLine: StateFlow<Int> = _currentLyricLine.asStateFlow()

    private val _lineProgress = MutableStateFlow(0f)
    val lineProgress: StateFlow<Float> = _lineProgress.asStateFlow()

    private val _lyricsLoading = MutableStateFlow(false)
    val lyricsLoading: StateFlow<Boolean> = _lyricsLoading.asStateFlow()

    private val _lyricsError = MutableStateFlow<String?>(null)
    val lyricsError: StateFlow<String?> = _lyricsError.asStateFlow()

    private val _lyricsOffsetMs = MutableStateFlow(0L)
    val lyricsOffsetMs: StateFlow<Long> = _lyricsOffsetMs.asStateFlow()

    fun bind(scope: CoroutineScope, progressFlow: StateFlow<Long>) {
        scope.launch {
            progressFlow.collect { progressMs ->
                trackLineProgress(_lyrics.value, progressMs, _lyricsOffsetMs.value)
            }
        }
    }

    suspend fun loadLyrics(song: Song) {
        try {
            val dbLyrics = musicRepo.loadLyrics(song.id)
            if (dbLyrics != null) {
                val (syncedLyrics, plainText) = dbLyrics
                if (!syncedLyrics.isNullOrBlank()) {
                    val simplified = toSimplifiedText(syncedLyrics)
                    val simplifiedPlain = plainText?.let { toSimplifiedText(it) }
                    val parsed = LrcParser.parse(simplified)
                    if (parsed.isNotEmpty()) {
                        _lyrics.value = parsed
                        _lyricsLoading.value = false
                        _lyricsError.value = null
                        _lyricsOffsetMs.value = musicRepo.loadLyricsOffset(song.id)
                        lyricsFetcher.restoreToCache(
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
                    val simplifiedPlain = toSimplifiedText(plainText)
                    _lyrics.value = emptyList()
                    _lyricsLoading.value = false
                    _lyricsError.value = "仅找到纯文本歌词，暂无同步时间轴"
                    _lyricsOffsetMs.value = musicRepo.loadLyricsOffset(song.id)
                    lyricsFetcher.restoreToCache(
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
            val result = lyricsFetcher.fetchSync(
                songId = song.id,
                title = song.title,
                artist = SearchMatch.cleanOptional(song.artist),
                album = SearchMatch.cleanOptional(song.album)
            )
            if (result != null && (result.syncedLines.isNotEmpty() || !result.plainText.isNullOrBlank())) {
                val rawLrc = result.rawSyncedLyrics ?: result.syncedLines.joinToString("\n") { line ->
                    val mins = line.timestamp / 60000
                    val secs = (line.timestamp % 60000) / 1000
                    val millis = line.timestamp % 1000
                    "[%02d:%02d.%03d]%s".format(mins, secs, millis, line.text)
                }
                musicRepo.saveLyrics(song.id, rawLrc.takeIf { it.isNotBlank() }, result.plainText)
                _lyricsOffsetMs.value = musicRepo.loadLyricsOffset(song.id)

                if (result.syncedLines.isNotEmpty()) {
                    _lyrics.value = result.syncedLines
                    _lyricsError.value = null
                } else {
                    _lyrics.value = emptyList()
                    _lyricsError.value = "仅找到纯文本歌词，暂无同步时间轴"
                }
            } else {
                _lyrics.value = emptyList()
                _lyricsError.value = "未找到同步歌词"
            }
        } catch (e: Exception) {
            MuseLog.w("LyricsStateHolder", "Lyrics fetch failed", e)
            _lyrics.value = emptyList()
            _lyricsError.value = "歌词获取失败"
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

    fun resetLyrics(scope: CoroutineScope, song: Song) {
        scope.launch {
            _lyrics.value = emptyList()
            _currentLyricLine.value = -1
            _lyricsError.value = null
            _lyricsLoading.value = true
            musicRepo.deleteLyrics(song.id)
            lyricsFetcher.clearCache()
            fetchLyrics(song)
        }
    }

    fun adjustLyricsOffset(scope: CoroutineScope, songId: Long, deltaMs: Long) {
        val newOffset = (_lyricsOffsetMs.value + deltaMs).coerceIn(-LYRIC_OFFSET_MAX_MS, LYRIC_OFFSET_MAX_MS)
        _lyricsOffsetMs.value = newOffset
        scope.launch {
            musicRepo.saveLyricsOffset(songId, newOffset)
        }
    }

    fun resetLyricsOffset(scope: CoroutineScope, songId: Long) {
        _lyricsOffsetMs.value = 0L
        scope.launch {
            musicRepo.saveLyricsOffset(songId, 0L)
        }
    }

    fun clear() {
        _lyrics.value = emptyList()
        _currentLyricLine.value = -1
        _lyricsError.value = null
    }
}
