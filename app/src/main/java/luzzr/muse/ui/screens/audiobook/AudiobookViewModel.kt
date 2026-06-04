package luzzr.muse.ui.screens.audiobook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import luzzr.muse.data.model.Song
import luzzr.muse.data.repository.MusicRepositoryFacade
import luzzr.muse.domain.model.BookCollection
import luzzr.muse.domain.model.BookCollectionItem
import luzzr.muse.domain.repository.BookCollectionRepository
import luzzr.muse.player.PlayerState
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AudiobookViewModel @Inject constructor(
    private val musicRepo: MusicRepositoryFacade,
    private val bookCollectionRepo: BookCollectionRepository,
    private val playerState: PlayerState
) : ViewModel() {

    // 所有的有声书 (codec == OGG)
    val audiobooks: StateFlow<List<Song>> = musicRepo.audiobooks

    // 最近收听的有声书 (按最近播放时间降序排列)
    val recentAudiobooks: StateFlow<List<Song>> = musicRepo.audiobooks.map { list ->
        list.filter { song ->
            playerState.getSongLastPlayedTime(song.id) > 0L
        }.sortedByDescending { song ->
            playerState.getSongLastPlayedTime(song.id)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 所有的书籍合集
    val collections: StateFlow<List<BookCollection>> = bookCollectionRepo.getAllCollections()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 当前选中的合集 ID
    private val _selectedCollectionId = MutableStateFlow<Long?>(null)
    val selectedCollectionId: StateFlow<Long?> = _selectedCollectionId.asStateFlow()

    // 当前选中合集的章节列表 (根据 sortOrder 升序)
    val currentCollectionItems: StateFlow<List<BookCollectionItem>> = _selectedCollectionId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList())
        else bookCollectionRepo.getItemsForCollection(id)
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
            // 限制序号在 1 到 99 之间
            val safeOrder = sortOrder.coerceIn(1, 99)
            bookCollectionRepo.updateItemSortOrder(collectionId, songId, safeOrder)
        }
    }

    // 播放单个有声书 (长音频自动从记录进度起播)
    fun playAudiobook(song: Song) {
        playerState.playSongs(listOf(song), 0)
    }

    // 播放合集
    fun playCollection(items: List<BookCollectionItem>, startIndex: Int = 0) {
        if (items.isEmpty()) return
        val songs = items.map { it.song }
        playerState.playSongs(songs, startIndex)
    }

    // 获取特定有声书的已听进度百分比 (0 到 100)
    fun getSavedProgressPercent(song: Song): Int {
        val saved = playerState.getSavedSongProgress(song.id)
        return if (song.duration > 0) {
            ((saved * 100) / song.duration).coerceIn(0, 100).toInt()
        } else {
            0
        }
    }
}
