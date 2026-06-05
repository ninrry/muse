package luzzr.muse.ui.screens.audiobook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import luzzr.muse.core.result.OperationResult
import luzzr.muse.domain.model.BookCollection
import luzzr.muse.domain.model.BookCollectionItem
import luzzr.muse.domain.model.Song
import luzzr.muse.domain.repository.BookCollectionRepository
import luzzr.muse.domain.repository.SongRepository
import luzzr.muse.domain.usecase.EditSongMetadataUseCase
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

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AudiobookViewModel @Inject constructor(
    private val songRepository: SongRepository,
    private val bookCollectionRepo: BookCollectionRepository,
    private val playbackController: PlaybackController,
    private val playbackActionController: PlaybackActionController,
    private val editSongMetadataUseCase: EditSongMetadataUseCase,
    private val updateSongArtworkUseCase: UpdateSongArtworkUseCase
) : ViewModel() {

    private val _editState = MutableStateFlow(AudiobookEditState())
    val songToEdit: StateFlow<Song?> = _editState.map { it.song }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val editError: StateFlow<UiText?> = _editState.map { it.error }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val isSavingMetadata: StateFlow<Boolean> = _editState.map {
        it.isSaving
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

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
            val editResult = editSongMetadataUseCase(
                song = song,
                title = title,
                artist = artist,
                album = album,
                year = yearStr.toIntOrNull(),
                genre = genre
            )
            if (editResult is OperationResult.Failure) {
                _editState.value = _editState.value.copy(error = editResult.toUiText(), isSaving = false)
            } else {
                if (artworkBytes != null) {
                    val artworkResult = updateSongArtworkUseCase(song, artworkBytes)
                    if (artworkResult is OperationResult.Failure) {
                        _editState.value = _editState.value.copy(error = artworkResult.toUiText(), isSaving = false)
                        return@launch
                    }
                }
                _editState.value = AudiobookEditState()
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
}
