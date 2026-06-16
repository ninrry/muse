package luzzr.muse.ui.screens.audiobook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import luzzr.muse.core.log.MuseLog
import luzzr.muse.core.result.OperationError
import luzzr.muse.core.result.OperationResult
import luzzr.muse.domain.model.BookCollection
import luzzr.muse.domain.model.BookCollectionItem
import luzzr.muse.domain.model.Song
import luzzr.muse.domain.repository.BookCollectionRepository
import luzzr.muse.domain.repository.EbookMetadataRepository
import luzzr.muse.domain.repository.SongRepository
import luzzr.muse.domain.usecase.EditSongMetadataUseCase
import luzzr.muse.domain.usecase.ImportBookCollectionMetadataUseCase
import luzzr.muse.domain.usecase.UpdateSongArtworkUseCase
import luzzr.muse.feature.audiobook.R
import luzzr.muse.media.PlaybackActionController
import luzzr.muse.media.PlaybackController
import luzzr.muse.ui.state.UiText
import luzzr.muse.ui.state.toUiText
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AudiobookViewModel @Inject constructor(
    private val songRepository: SongRepository,
    private val bookCollectionRepo: BookCollectionRepository,
    private val playbackController: PlaybackController,
    private val playbackActionController: PlaybackActionController,
    private val editSongMetadataUseCase: EditSongMetadataUseCase,
    private val updateSongArtworkUseCase: UpdateSongArtworkUseCase,
    private val ebookMetadataRepository: EbookMetadataRepository,
    private val importBookCollectionMetadataUseCase: ImportBookCollectionMetadataUseCase
) : ViewModel() {

    private val _editState = MutableStateFlow(AudiobookEditState())
    val songToEdit: StateFlow<Song?> = _editState.map { it.song }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val editError: StateFlow<UiText?> = _editState.map { it.error }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val isSavingMetadata: StateFlow<Boolean> = _editState.map {
        it.isSaving
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _importState = MutableStateFlow(AudiobookImportState())
    val importState: StateFlow<AudiobookImportState> = _importState.asStateFlow()

    fun requestEditMetadata(song: Song) {
        _editState.value = AudiobookEditState(song = song)
    }

    fun cancelEditMetadata() {
        _editState.value = AudiobookEditState()
    }

    fun saveEditedMetadata(title: String, artist: String, album: String, yearStr: String, genre: String, artworkBytes: ByteArray? = null) {
        val song = _editState.value.song ?: return
        if (title.isBlank()) {
            _editState.value = _editState.value.copy(error = UiText.Resource(R.string.audiobook_name_empty))
            return
        }
        _editState.value = _editState.value.copy(error = null, isSaving = true)
        viewModelScope.launch {
            try {
                val editResult = editSongMetadataUseCase(
                    song = song,
                    title = title,
                    artist = artist,
                    album = album,
                    year = yearStr.toIntOrNull(),
                    genre = genre
                )
                if (editResult is OperationResult.Failure) {
                    showEditFailure(editResult)
                    return@launch
                }
                if (artworkBytes != null) {
                    val artworkTarget = findCurrentSong(song.id) ?: song
                    val artworkResult = updateSongArtworkUseCase(artworkTarget, artworkBytes)
                    if (artworkResult is OperationResult.Failure) {
                        showEditFailure(artworkResult)
                        return@launch
                    }
                }
                _editState.value = AudiobookEditState()
            } catch (e: IOException) {
                MuseLog.w("AudiobookViewModel", "Metadata edit IO failed", e)
                showEditFailure(OperationResult.Failure(OperationError.IO, e.message))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: android.database.sqlite.SQLiteException) {
                MuseLog.e("AudiobookViewModel", "Metadata edit database failed", e)
                showEditFailure(OperationResult.Failure(OperationError.DATABASE, e.message))
            } catch (e: SecurityException) {
                MuseLog.e("AudiobookViewModel", "Metadata edit permission denied", e)
                showEditFailure(OperationResult.Failure(OperationError.PERMISSION_DENIED, e.message))
            } catch (e: OutOfMemoryError) {
                MuseLog.e("AudiobookViewModel", "Metadata edit ran out of memory", e)
                Runtime.getRuntime().gc()
                showEditFailure(OperationResult.Failure(OperationError.UNKNOWN, e.message))
            } catch (e: Exception) {
                MuseLog.e("AudiobookViewModel", "Metadata edit failed unexpectedly", e)
                showEditFailure(OperationResult.Failure(OperationError.UNKNOWN, e.message))
            } finally {
                if (_editState.value.song?.id == song.id) {
                    _editState.value = _editState.value.copy(isSaving = false)
                }
            }
        }
    }

    val audiobooks: StateFlow<List<Song>> = songRepository.audiobooks

    val recentAudiobooks: StateFlow<List<Song>> = songRepository.audiobooks.map { list ->
        list.filter { song ->
            playbackController.getSongLastPlayedTime(song.id) > 0L
        }.sortedByDescending { song ->
            playbackController.getSongLastPlayedTime(song.id)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val collections: StateFlow<List<BookCollection>> = bookCollectionRepo.getAllCollections()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedCollectionId = MutableStateFlow<Long?>(null)
    val selectedCollectionId: StateFlow<Long?> = _selectedCollectionId.asStateFlow()

    val currentCollectionItems: StateFlow<List<BookCollectionItem>> = _selectedCollectionId.flatMapLatest { id ->
        if (id == null) {
            flowOf(emptyList())
        } else {
            bookCollectionRepo.getItemsForCollection(id)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectCollection(collectionId: Long?) {
        _selectedCollectionId.value = collectionId
    }

    fun createCollection(name: String) {
        viewModelScope.launch {
            bookCollectionRepo.createCollection(name)
        }
    }

    fun deleteCollection(collectionId: Long) {
        viewModelScope.launch {
            if (_selectedCollectionId.value == collectionId) {
                _selectedCollectionId.value = null
            }
            bookCollectionRepo.deleteCollection(collectionId)
        }
    }

    fun addSongsToCollection(collectionId: Long, songIds: List<Long>) {
        viewModelScope.launch {
            bookCollectionRepo.addItemsToCollection(collectionId, songIds)
        }
    }

    fun removeSongFromCollection(collectionId: Long, songId: Long) {
        viewModelScope.launch {
            bookCollectionRepo.removeItemFromCollection(collectionId, songId)
        }
    }

    fun updateSongSortOrder(collectionId: Long, songId: Long, sortOrder: Int) {
        viewModelScope.launch {
            val safeOrder = sortOrder.coerceIn(MIN_SORT_ORDER, MAX_SORT_ORDER)
            bookCollectionRepo.updateItemSortOrder(collectionId, songId, safeOrder)
        }
    }

    fun requestEbookPreview(uri: String, displayName: String?, mimeType: String?) {
        val collectionId = _selectedCollectionId.value ?: return
        if (_importState.value.isParsing || _importState.value.isImporting) return
        _importState.value = AudiobookImportState(isParsing = true)
        viewModelScope.launch {
            when (val extraction = ebookMetadataRepository.extract(uri, displayName, mimeType)) {
                is OperationResult.Failure -> {
                    _importState.value = AudiobookImportState(error = extraction.toUiText())
                }
                is OperationResult.Success -> {
                    val collection = bookCollectionRepo.getCollection(collectionId)
                    if (collection == null || _selectedCollectionId.value != collectionId) {
                        _importState.value = AudiobookImportState()
                        return@launch
                    }
                    val items = bookCollectionRepo.getItemsForCollectionSync(collectionId)
                    val finalTitle = extraction.value.title.trim().ifBlank { collection.name }
                    _importState.value = AudiobookImportState(
                        preview = EbookImportPreview(
                            sourceUri = uri,
                            displayName = displayName,
                            mimeType = mimeType,
                            metadata = extraction.value,
                            finalTitle = finalTitle,
                            chapterCount = items.size,
                            exampleTitle = items.firstOrNull()?.let {
                                "$finalTitle ${it.sortOrder.toString().padStart(2, '0')}"
                            }
                        )
                    )
                }
            }
        }
    }

    fun confirmEbookImport() {
        val collectionId = _selectedCollectionId.value ?: return
        val preview = _importState.value.preview ?: return
        if (_importState.value.isImporting) return
        _importState.value = _importState.value.copy(
            isImporting = true,
            completedCount = 0,
            totalCount = preview.chapterCount,
            error = null
        )
        viewModelScope.launch {
            when (
                val importResult = importBookCollectionMetadataUseCase(
                    collectionId = collectionId,
                    metadata = preview.metadata,
                    onProgress = { completed, total ->
                        _importState.value = _importState.value.copy(completedCount = completed, totalCount = total)
                    }
                )
            ) {
                is OperationResult.Failure -> {
                    _importState.value = AudiobookImportState(error = importResult.toUiText())
                }
                is OperationResult.Success -> {
                    _importState.value = AudiobookImportState(result = importResult.value)
                }
            }
        }
    }

    fun dismissEbookPreview() {
        if (!_importState.value.isImporting) _importState.value = AudiobookImportState()
    }

    fun dismissEbookImportResult() {
        _importState.value = AudiobookImportState()
    }

    fun playAudiobook(song: Song) {
        playbackActionController.playSongAtIndex(listOf(song), 0)
    }

    fun playCollection(items: List<BookCollectionItem>, startIndex: Int = 0) {
        if (items.isEmpty()) return
        playbackActionController.playSongAtIndex(items.map { it.song }, startIndex)
    }

    fun getSavedProgressPercent(song: Song): Int {
        val saved = playbackController.getSavedSongProgress(song.id)
        return if (song.duration > 0) {
            ((saved * PERCENT_SCALE) / song.duration).coerceIn(0, PERCENT_SCALE).toInt()
        } else {
            0
        }
    }

    private companion object {
        const val MIN_SORT_ORDER = 1
        const val MAX_SORT_ORDER = 99
        const val PERCENT_SCALE = 100L
    }

    private fun findCurrentSong(songId: Long): Song? {
        return songRepository.audiobooks.value.find { it.id == songId }
            ?: songRepository.songs.value.find { it.id == songId }
    }

    private fun showEditFailure(failure: OperationResult.Failure) {
        _editState.value = _editState.value.copy(error = failure.toUiText(), isSaving = false)
    }
}
