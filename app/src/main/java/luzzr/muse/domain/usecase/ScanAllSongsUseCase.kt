package luzzr.muse.domain.usecase

import luzzr.muse.core.log.MuseLog
import luzzr.muse.domain.model.Song
import luzzr.muse.domain.repository.SongRepository
import luzzr.muse.domain.scanner.ScanHistoryStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScanAllSongsUseCase @Inject constructor(
    private val songRepository: SongRepository,
    private val scanHistoryStore: ScanHistoryStore
) {
    suspend operator fun invoke(): List<Song> {
        if (songRepository.isScanning.value) {
            MuseLog.d("ScanAllSongsUseCase", "Scan already in progress, skipping")
            return songRepository.songs.value
        }
        val result = songRepository.scanAll()
        scanHistoryStore.markScanCompleted(System.currentTimeMillis())
        MuseLog.d("ScanAllSongsUseCase", "Scan completed: ${result.size} songs")
        return result
    }
}
