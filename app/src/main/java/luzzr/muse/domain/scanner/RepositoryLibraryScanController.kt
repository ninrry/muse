package luzzr.muse.domain.scanner

import luzzr.muse.domain.model.ScanStats
import luzzr.muse.domain.model.Song
import luzzr.muse.domain.repository.SongRepository
import luzzr.muse.domain.usecase.ScanAllSongsUseCase
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.StateFlow

@Singleton
class RepositoryLibraryScanController @Inject constructor(
    private val songRepository: SongRepository,
    private val scanAllSongsUseCase: ScanAllSongsUseCase
) : LibraryScanController {

    override val songs: StateFlow<List<Song>> = songRepository.songs
    override val isScanning: StateFlow<Boolean> = songRepository.isScanning
    override val scanProgress: StateFlow<Int> = songRepository.scanProgress
    override val scanStats: StateFlow<ScanStats?> = songRepository.scanStats

    override suspend fun scanAll(): List<Song> = scanAllSongsUseCase()
}
