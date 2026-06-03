package luzzr.muse.domain.usecase

import android.content.SharedPreferences
import luzzr.muse.core.log.MuseLog
import luzzr.muse.domain.model.Song
import luzzr.muse.domain.repository.SongRepository
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class ScanAllSongsUseCase @Inject constructor(
    private val songRepository: SongRepository,
    @Named("scan_prefs") private val scanPrefs: SharedPreferences
) {
    suspend operator fun invoke(): List<Song> {
        if (songRepository.isScanning.value) {
            MuseLog.d("ScanAllSongsUseCase", "Scan already in progress, skipping")
            return songRepository.songs.value
        }
        val result = songRepository.scanAll()
        scanPrefs.edit().putLong("last_scan_time", System.currentTimeMillis()).apply()
        MuseLog.d("ScanAllSongsUseCase", "Scan completed: ${result.size} songs")
        return result
    }
}
