package luzzr.muse.ui.screens.library

import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import luzzr.muse.data.model.Album
import luzzr.muse.data.model.Artist
import luzzr.muse.data.model.Song
import luzzr.muse.data.model.SortType
import luzzr.muse.data.network.LyricsFetcher
import luzzr.muse.data.network.MetadataFetcher
import luzzr.muse.data.network.MetadataResult
import luzzr.muse.data.network.SearchMatch
import luzzr.muse.data.network.toSimplifiedText
import luzzr.muse.data.repository.MusicRepositoryFacade
import luzzr.muse.domain.usecase.DeleteSongUseCase
import luzzr.muse.player.PlayerState
import luzzr.muse.ui.state.LibraryEditState
import luzzr.muse.ui.state.LibraryMetadataState
import luzzr.muse.ui.state.LibrarySearchState
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class LibraryViewModel @Inject constructor(
    application: Application,
    private val repository: MusicRepositoryFacade,
    private val playerState: PlayerState,
    private val lyricsFetcher: LyricsFetcher,
    private val metadataFetcher: MetadataFetcher,
    private val playerControlUseCase: luzzr.muse.domain.usecase.PlayerControlUseCase,
    private val editSongMetadataUseCase: luzzr.muse.domain.usecase.EditSongMetadataUseCase,
    private val getAlbumsUseCase: luzzr.muse.domain.usecase.GetAlbumsUseCase,
    private val getArtistsUseCase: luzzr.muse.domain.usecase.GetArtistsUseCase,
    private val getSongsByAlbumUseCase: luzzr.muse.domain.usecase.GetSongsByAlbumUseCase,
    private val getSongsByArtistUseCase: luzzr.muse.domain.usecase.GetSongsByArtistUseCase,
    private val searchSongsUseCase: luzzr.muse.domain.usecase.SearchSongsUseCase,
    private val renameSongUseCase: luzzr.muse.domain.usecase.RenameSongUseCase,
    private val applyMetadataUseCase: luzzr.muse.domain.usecase.ApplyMetadataUseCase,
    private val updateSongArtworkUseCase: luzzr.muse.domain.usecase.UpdateSongArtworkUseCase,
    private val deleteLyricsUseCase: luzzr.muse.domain.usecase.DeleteLyricsUseCase,
    private val refreshAlbumAndArtistTablesUseCase: luzzr.muse.domain.usecase.RefreshAlbumAndArtistTablesUseCase,
    private val deleteSongUseCase: DeleteSongUseCase
) : AndroidViewModel(application) {

    private val _sortType = MutableStateFlow(SortType.TITLE_ASC)
    val sortType: StateFlow<SortType> = _sortType.asStateFlow()

    val songs: StateFlow<List<Song>> = combine(
        repository.songs,
        _sortType
    ) { allSongs, sort ->
        if (allSongs.isEmpty()) {
            allSongs
        } else {
            sortSongs(allSongs, sort)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val isScanning: StateFlow<Boolean> = repository.isScanning

    val currentSong: StateFlow<Song?> = playerState.currentSong
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums.asStateFlow()

    private val _artists = MutableStateFlow<List<Artist>>(emptyList())
    val artists: StateFlow<List<Artist>> = _artists.asStateFlow()

    private val _albumDetail = MutableStateFlow<Pair<Album, List<Song>>?>(null)
    val albumDetail: StateFlow<Pair<Album, List<Song>>?> = _albumDetail.asStateFlow()

    private val _artistDetail = MutableStateFlow<Pair<Artist, List<Song>>?>(null)
    val artistDetail: StateFlow<Pair<Artist, List<Song>>?> = _artistDetail.asStateFlow()

    private val _searchState = MutableStateFlow(LibrarySearchState())
    val searchQuery: StateFlow<String> = _searchState.map { it.query }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
    val searchResults: StateFlow<List<Song>> = _searchState.map { it.results }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _songToDelete = MutableStateFlow<Song?>(null)
    val songToDelete: StateFlow<Song?> = _songToDelete.asStateFlow()
    private val _songToRename = MutableStateFlow<Song?>(null)
    val songToRename: StateFlow<Song?> = _songToRename.asStateFlow()
    private val _metadataState = MutableStateFlow(LibraryMetadataState())
    val metadataSong: StateFlow<Song?> = _metadataState.map { it.song }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val metadataResults: StateFlow<List<MetadataResult>> = _metadataState.map { it.results }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val metadataLoading: StateFlow<Boolean> = _metadataState.map { it.isFetching }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val metadataError: StateFlow<String?> = _metadataState.map { it.error }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val showSearchTermsDialog: StateFlow<Song?> = _metadataState.map { it.searchTermsSong }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val _editState = MutableStateFlow(LibraryEditState())
    val songToEdit: StateFlow<Song?> = _editState.map { it.song }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val editError: StateFlow<String?> = _editState.map { it.error }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val needsStoragePermission: StateFlow<Boolean> = _editState.map { it.needsStoragePermission }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    init {
        refreshStats()
        viewModelScope.launch {
            repository.coverGenerationCompleted.collect {
                refreshStats()
            }
        }
    }

    fun setSortType(type: SortType) {
        _sortType.value = type
    }

    fun cycleSortType() {
        val types = SortType.entries.toList()
        val nextIndex = (types.indexOf(_sortType.value) + 1) % types.size
        _sortType.value = types[nextIndex]
    }

    private fun sortSongs(songs: List<Song>, type: SortType): List<Song> {
        return when (type) {
            SortType.TITLE_ASC -> songs.sortedBy { it.title }
            SortType.TITLE_DESC -> songs.sortedByDescending { it.title }
            SortType.ARTIST_ASC -> songs.sortedBy { it.artist }
            SortType.ARTIST_DESC -> songs.sortedByDescending { it.artist }
            SortType.ALBUM_ASC -> songs.sortedBy { it.album }
            SortType.ALBUM_DESC -> songs.sortedByDescending { it.album }
            SortType.DURATION_ASC -> songs.sortedBy { it.duration }
            SortType.DURATION_DESC -> songs.sortedByDescending { it.duration }
            SortType.DATE_ADDED_DESC -> songs.sortedByDescending { it.dateAdded }
            SortType.DATE_ADDED_ASC -> songs.sortedBy { it.dateAdded }
        }
    }

    fun refreshStats() {
        viewModelScope.launch {
            _albums.value = getAlbumsUseCase()
            _artists.value = getArtistsUseCase()
        }
    }

    fun search(query: String) {
        _searchState.value = _searchState.value.copy(query = query)
        if (query.isBlank()) {
            _searchState.value = _searchState.value.copy(results = emptyList())
            return
        }
        viewModelScope.launch {
            _searchState.value = _searchState.value.copy(results = searchSongsUseCase(query))
        }
    }

    fun playSongs(songList: List<Song>, startIndex: Int = 0) {
        playerControlUseCase.playSongAtIndex(songList, startIndex)
    }

    fun playShuffled(songList: List<Song>) {
        if (songList.isEmpty()) return
        playerControlUseCase.playSongAtIndex(songList, 0)
        if (!playerState.shuffleMode.value) {
            playerState.toggleShuffle()
        }
    }

    fun playAll(startIndex: Int = 0) {
        val list = if (_searchState.value.query.isNotBlank()) _searchState.value.results else songs.value
        if (list.isNotEmpty()) playSongs(list, startIndex)
    }

    fun getSongsByAlbum(album: String, callback: (List<Song>) -> Unit) {
        viewModelScope.launch {
            val songs = getSongsByAlbumUseCase(album)
            withContext(Dispatchers.Main) { callback(songs) }
        }
    }

    fun showAlbumSongs(album: Album) {
        viewModelScope.launch {
            val songs = getSongsByAlbumUseCase(album.title)
            _albumDetail.value = album to songs
        }
    }

    fun dismissAlbumDetail() {
        _albumDetail.value = null
    }
    fun showArtistSongs(artist: Artist) {
        viewModelScope.launch {
            val songs = getSongsByArtistUseCase(artist.name)
            _artistDetail.value = artist to songs
        }
    }

    fun dismissArtistDetail() {
        _artistDetail.value = null
    }
    fun getSongsByArtist(artist: String, callback: (List<Song>) -> Unit) {
        viewModelScope.launch {
            val songs = getSongsByArtistUseCase(artist)
            withContext(Dispatchers.Main) { callback(songs) }
        }
    }

    fun requestDeleteSong(song: Song) {
        _songToDelete.value = song
    }
    fun cancelDelete() {
        _songToDelete.value = null
    }
    fun confirmDelete() {
        val song = _songToDelete.value ?: return
        viewModelScope.launch {
            deleteSongUseCase(song)
            _songToDelete.value = null
            refreshStats()
        }
    }

    fun requestRenameSong(song: Song) {
        _songToRename.value = song
    }
    fun cancelRename() {
        _songToRename.value = null
    }

    fun confirmRename(newTitle: String) {
        val song = _songToRename.value ?: return
        viewModelScope.launch {
            renameSongUseCase(song, newTitle)
            _songToRename.value = null
        }
    }

    fun requestSearchMetadata(song: Song) {
        _metadataState.value = _metadataState.value.copy(searchTermsSong = song)
    }

    fun cancelSearchTerms() {
        _metadataState.value = _metadataState.value.copy(searchTermsSong = null)
    }

    fun searchMetadataExact(title: String, artist: String) {
        val song = _metadataState.value.searchTermsSong ?: return
        _metadataState.value = _metadataState.value.copy(
            searchTermsSong = null,
            song = song,
            results = emptyList(),
            error = null,
            isFetching = true
        )
        viewModelScope.launch {
            try {
                val results = metadataFetcher.searchExact(
                    title = title,
                    artist = artist.ifBlank { null }
                )
                _metadataState.value = _metadataState.value.copy(results = results)
                if (results.isEmpty()) {
                    _metadataState.value = _metadataState.value.copy(error = "未找到匹配结果，请尝试修改搜索关键词后重试")
                }
            } catch (e: java.io.IOException) {
                _metadataState.value = _metadataState.value.copy(error = "网络请求失败: ${e.localizedMessage ?: "网络连接错误"}")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: IllegalStateException) {
                _metadataState.value = _metadataState.value.copy(error = "请求失败: ${e.localizedMessage ?: "状态错误"}")
            } finally {
                _metadataState.value = _metadataState.value.copy(isFetching = false)
            }
        }
    }

    fun searchMetadata(song: Song) {
        _metadataState.value = _metadataState.value.copy(
            song = song,
            results = emptyList(),
            error = null,
            isFetching = true
        )
        viewModelScope.launch {
            try {
                val results = metadataFetcher.search(
                    rawTitle = song.title,
                    rawArtist = SearchMatch.cleanOptional(song.artist)
                )
                _metadataState.value = _metadataState.value.copy(results = results)
                if (results.isEmpty()) {
                    _metadataState.value = _metadataState.value.copy(error = "未找到匹配结果，请尝试修改搜索关键词后重试")
                }
            } catch (e: java.io.IOException) {
                _metadataState.value = _metadataState.value.copy(error = "网络请求失败: ${e.localizedMessage ?: "网络连接错误"}")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: IllegalStateException) {
                _metadataState.value = _metadataState.value.copy(error = "请求失败: ${e.localizedMessage ?: "状态错误"}")
            } finally {
                _metadataState.value = _metadataState.value.copy(isFetching = false)
            }
        }
    }

    fun applyMetadataResult(result: MetadataResult) {
        val song = _metadataState.value.song ?: return
        _metadataState.value = _metadataState.value.copy(isFetching = true)
        viewModelScope.launch {
            try {
                applyMetadataUseCase(song, result)
                deleteLyricsUseCase(song.id)
                lyricsFetcher.clearCache()
                if (!result.coverUrl.isNullOrBlank()) {
                    val bytes = repository.downloadBytes(result.coverUrl)
                    if (bytes != null) {
                        updateSongArtworkUseCase(song, bytes)
                    }
                }
                refreshAlbumAndArtistTablesUseCase()
                closeMetadataSheet()
            } catch (e: java.io.IOException) {
                _metadataState.value = _metadataState.value.copy(error = "保存失败: ${e.localizedMessage ?: "网络连接错误"}")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: android.database.sqlite.SQLiteException) {
                _metadataState.value = _metadataState.value.copy(error = "保存失败: ${e.localizedMessage ?: "数据库错误"}")
            } finally {
                _metadataState.value = _metadataState.value.copy(isFetching = false)
            }
        }
    }

    fun closeMetadataSheet() {
        _metadataState.value = LibraryMetadataState()
    }

    private fun hasFullFileAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    fun requestStoragePermission() {
        val ctx = getApplication<Application>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = android.net.Uri.parse("package:${ctx.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
        }
        _editState.value = _editState.value.copy(needsStoragePermission = false)
    }

    fun requestEditMetadata(song: Song) {
        if (!hasFullFileAccess()) {
            _editState.value = _editState.value.copy(needsStoragePermission = true)
            return
        }
        _editState.value = _editState.value.copy(song = song, error = null)
    }

    fun dismissPermissionDialog() {
        _editState.value = _editState.value.copy(needsStoragePermission = false)
    }

    fun cancelEditMetadata() {
        _editState.value = LibraryEditState()
    }

    fun saveEditedMetadata(title: String, artist: String, album: String, yearStr: String, genre: String, artworkBytes: ByteArray? = null) {
        val song = _editState.value.song ?: return
        if (title.isBlank()) {
            _editState.value = _editState.value.copy(error = "歌曲名不能为空")
            return
        }
        val year = yearStr.toIntOrNull()
        _editState.value = _editState.value.copy(song = null)
        viewModelScope.launch {
            val converter = ::toSimplifiedText
            val simpleTitle = converter(title)
            val simpleArtist = converter(artist)
            val simpleAlbum = converter(album)
            val simpleGenre = converter(genre)

            editSongMetadataUseCase(
                song = song,
                title = simpleTitle,
                artist = simpleArtist,
                album = simpleAlbum,
                year = year,
                genre = simpleGenre
            )
            if (simpleTitle != song.title || simpleArtist != song.artist || simpleAlbum != song.album) {
                deleteLyricsUseCase(song.id)
                lyricsFetcher.clearCache()
            }
            if (artworkBytes != null) {
                updateSongArtworkUseCase(song, artworkBytes)
            }
            refreshAlbumAndArtistTablesUseCase()
            refreshStats()
        }
    }
}
