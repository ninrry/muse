package luzzr.muse.domain.usecase

import luzzr.muse.domain.repository.ArtworkRepository
import luzzr.muse.domain.repository.SongRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case for loading the music library.
 *
 * Orchestrates the library loading logic:
 * 1. Load songs from database
 * 2. If database is empty, scan for new songs
 * 3. Otherwise, generate any missing album artwork
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
        val loadedSongs = songRepository.loadFromDatabase()

        return if (loadedSongs.isEmpty()) {
            // Database is empty, need to scan for music
            songRepository.scanAll()
            true
        } else {
            // Database has songs, ensure all have artwork
            artworkRepository.generateMissingCovers()
            true
        }
    }
}