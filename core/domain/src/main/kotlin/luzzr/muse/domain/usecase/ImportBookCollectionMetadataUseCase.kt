package luzzr.muse.domain.usecase

import luzzr.muse.core.result.OperationError
import luzzr.muse.core.result.OperationResult
import luzzr.muse.domain.model.BookCollectionImportFailure
import luzzr.muse.domain.model.BookCollectionImportFailureStage
import luzzr.muse.domain.model.BookCollectionImportResult
import luzzr.muse.domain.model.EbookMetadata
import luzzr.muse.domain.repository.ArtworkRepository
import luzzr.muse.domain.repository.BookCollectionRepository
import luzzr.muse.domain.repository.SongRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImportBookCollectionMetadataUseCase @Inject constructor(
    private val bookCollectionRepository: BookCollectionRepository,
    private val songRepository: SongRepository,
    private val artworkRepository: ArtworkRepository
) {
    suspend operator fun invoke(
        collectionId: Long,
        metadata: EbookMetadata,
        onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> }
    ): OperationResult<BookCollectionImportResult> {
        val collection = bookCollectionRepository.getCollection(collectionId)
            ?: return OperationResult.Failure(OperationError.NOT_FOUND, "Collection $collectionId was not found")
        val finalTitle = metadata.title.trim().ifBlank { collection.name }
        val finalAuthor = metadata.author.trim().ifBlank { collection.author }
        val coverBytes = metadata.coverBytes
        val collectionResult = bookCollectionRepository.updateCollectionMetadata(
            collectionId = collectionId,
            name = finalTitle,
            author = finalAuthor,
            artworkBytes = coverBytes
        )
        if (collectionResult is OperationResult.Failure) return collectionResult

        val items = bookCollectionRepository.getItemsForCollectionSync(collectionId)
        val failures = mutableListOf<BookCollectionImportFailure>()
        var successCount = 0
        onProgress(0, items.size)
        items.forEachIndexed { index, item ->
            val song = item.song
            val chapterTitle = "$finalTitle ${item.sortOrder.toString().padStart(2, '0')}"
            val chapterAuthor = metadata.author.trim().ifBlank { song.artist }
            val tagResult = songRepository.updateSongTags(
                song = song,
                title = chapterTitle,
                artist = chapterAuthor,
                album = finalTitle,
                year = song.year,
                genre = song.genre
            )
            if (tagResult is OperationResult.Failure) {
                failures += BookCollectionImportFailure(
                    songId = song.id,
                    originalTitle = song.title,
                    stage = BookCollectionImportFailureStage.METADATA,
                    message = tagResult.message
                )
            } else if (coverBytes != null) {
                val updatedSong = song.copy(title = chapterTitle, artist = chapterAuthor, album = finalTitle)
                val artworkResult = artworkRepository.updateSongArtwork(updatedSong, coverBytes)
                if (artworkResult is OperationResult.Failure) {
                    failures += BookCollectionImportFailure(
                        songId = song.id,
                        originalTitle = song.title,
                        stage = BookCollectionImportFailureStage.ARTWORK,
                        message = artworkResult.message
                    )
                } else {
                    successCount++
                }
            } else {
                successCount++
            }
            onProgress(index + 1, items.size)
        }
        songRepository.refreshAlbumAndArtistTables()
        return OperationResult.Success(
            BookCollectionImportResult(
                totalCount = items.size,
                successCount = successCount,
                failures = failures
            )
        )
    }
}
