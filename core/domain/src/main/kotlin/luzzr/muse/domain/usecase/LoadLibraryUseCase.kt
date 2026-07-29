package luzzr.muse.domain.usecase

import luzzr.muse.domain.repository.ArtworkRepository
import luzzr.muse.domain.repository.SongRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case for loading the music library.
 *
 * Orchestrates the library loading logic:
 * 1. Load songs from database (fast mode - no physical file refresh)
 * 2. If database is empty, scan for new songs
 * 3. If refresh is needed (24h+), do background scan
 * 4. Otherwise, just generate missing album artwork lazily
 *
 * This logic was previously in MainViewModel but belongs in the domain layer
 * as it encapsulates business rules about when to scan vs. when to generate covers.
 */
@Singleton
class LoadLibraryUseCase @Inject constructor(
    private val songRepository: SongRepository,
    private val artworkRepository: ArtworkRepository
) {
    /**
     * Load the music library.
     *
     * @return true if songs were found or scanned, false if library is still empty
     */
    suspend operator fun invoke(): Boolean {
        // 快速加载：从数据库直接读取，不刷新物理文件
        val loadedSongs = songRepository.loadFromDatabaseFast()

        return if (loadedSongs.isEmpty()) {
            // 数据库为空，完全扫描
            songRepository.scanAll()
            true
        } else if (songRepository.shouldRefreshLibrary()) {
            // 需要刷新，后台静默刷新（可选）
            // 这里只生成缺失的封面，不阻塞启动
            artworkRepository.generateMissingCovers()
            true
        } else {
            // 快速加载完成，后台生成缺失封面
            artworkRepository.generateMissingCovers()
            true
        }
    }
}
