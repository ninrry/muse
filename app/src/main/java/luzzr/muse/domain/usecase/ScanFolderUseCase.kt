package luzzr.muse.domain.usecase

import luzzr.muse.core.log.MuseLog
import luzzr.muse.domain.model.Song
import luzzr.muse.domain.repository.SongRepository
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScanFolderUseCase @Inject constructor(
    private val songRepository: SongRepository
) {
    suspend operator fun invoke(path: String): List<Song> {
        val dir = File(path)
        if (!dir.exists() || !dir.isDirectory) {
            MuseLog.w("ScanFolderUseCase", "Invalid path: $path")
            return emptyList()
        }
        if (!dir.canRead()) {
            MuseLog.w("ScanFolderUseCase", "No read permission for: $path")
            return emptyList()
        }
        return songRepository.scanFolder(path)
    }
}
