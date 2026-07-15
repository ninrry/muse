package luzzr.muse.ui.screens.library

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import luzzr.muse.core.log.MuseLog
import luzzr.muse.core.result.OperationError
import luzzr.muse.core.result.OperationResult
import luzzr.muse.domain.model.Album
import luzzr.muse.domain.model.Artist
import luzzr.muse.domain.model.MetadataResult
import luzzr.muse.domain.model.Playlist
import luzzr.muse.domain.model.Song
import luzzr.muse.domain.model.SortType
import luzzr.muse.domain.repository.ArtworkRepository
import luzzr.muse.domain.repository.PlaylistRepository
import luzzr.muse.domain.repository.SongRepository
import luzzr.muse.domain.text.TextNormalizer
import luzzr.muse.domain.usecase.ClearLyricsCacheUseCase
import luzzr.muse.domain.usecase.DeleteSongUseCase
import luzzr.muse.domain.usecase.SearchMetadataUseCase
import luzzr.muse.feature.library.R
import luzzr.muse.media.PlaybackActionController
import luzzr.muse.media.PlaybackController
import luzzr.muse.ui.state.LibraryEditState
import luzzr.muse.ui.state.LibraryMetadataState
import luzzr.muse.ui.state.LibrarySearchState
import luzzr.muse.ui.state.ShizukuPermissionController
import luzzr.muse.ui.state.StoragePermissionController
import luzzr.muse.ui.state.UiText
import luzzr.muse.ui.state.toUiText
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val songRepository: SongRepository,
    private val playlistRepository: PlaylistRepository,
    private val artworkRepository: ArtworkRepository,
    private val playbackController: PlaybackController,
    private val storagePermissionController: StoragePermissionController,
    private val shizukuPermissionController: ShizukuPermissionController,
    private val searchMetadataUseCase: SearchMetadataUseCase,
    private val textNormalizer: TextNormalizer,
    private val clearLyricsCacheUseCase: ClearLyricsCacheUseCase,
    private val playbackActionController: PlaybackActionController,
    private val editSongMetadataUseCase: luzzr.muse.domain.usecase.EditSongMetadataUseCase,
    private val getAlbumsUseCase: luzzr.muse.domain.usecase.GetAlbumsUseCase,
    private val getArtistsUseCase: luzzr.muse.domain.usecase.GetArtistsUseCase,
    private val getSongsByAlbumUseCase: luzzr.muse.domain.usecase.GetSongsByAlbumUseCase,
    private val getSongsByArtistUseCase: luzzr.muse.domain.usecase.GetSongsByArtistUseCase,
    private val searchSongsUseCase: luzzr.muse.domain.usecase.SearchSongsUseCase,
    private val applyMetadataUseCase: luzzr.muse.domain.usecase.ApplyMetadataUseCase,
    private val updateSongArtworkUseCase: luzzr.muse.domain.usecase.UpdateSongArtworkUseCase,
    private val deleteLyricsUseCase: luzzr.muse.domain.usecase.DeleteLyricsUseCase,
    private val refreshAlbumAndArtistTablesUseCase: luzzr.muse.domain.usecase.RefreshAlbumAndArtistTablesUseCase,
    private val deleteSongUseCase: DeleteSongUseCase
) : ViewModel() {

    private val _sortType = MutableStateFlow(SortType.TITLE_ASC)
    val sortType: StateFlow<SortType> = _sortType.asStateFlow()

    val songs: StateFlow<List<Song>> = combine(
        songRepository.songs,
        _sortType
    ) { allSongs, sort ->
        if (allSongs.isEmpty()) {
            allSongs
        } else {
            sortSongs(allSongs, sort)
        }
    }.distinctUntilChanged()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val isScanning: StateFlow<Boolean> = songRepository.isScanning

    val currentSong: StateFlow<Song?> = playbackController.state
        .map { it.currentSong }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums.asStateFlow()

    private val _artists = MutableStateFlow<List<Artist>>(emptyList())
    val artists: StateFlow<List<Artist>> = _artists.asStateFlow()

    val playlists: StateFlow<List<Playlist>> = playlistRepository.getAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
    private val _songToAddToPlaylist = MutableStateFlow<Song?>(null)
    val songToAddToPlaylist: StateFlow<Song?> = _songToAddToPlaylist.asStateFlow()
    private val _deleteError = MutableStateFlow<UiText?>(null)
    val deleteError: StateFlow<UiText?> = _deleteError.asStateFlow()
    private val _metadataState = MutableStateFlow(LibraryMetadataState())
    val metadataSong: StateFlow<Song?> = _metadataState.map { it.song }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val metadataResults: StateFlow<List<MetadataResult>> = _metadataState.map { it.results }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val metadataLoading: StateFlow<Boolean> = _metadataState.map { it.isFetching }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val metadataApplying: StateFlow<Boolean> = _metadataState.map { it.isApplying }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val metadataError: StateFlow<UiText?> = _metadataState.map { it.error }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val showSearchTermsDialog: StateFlow<Song?> = _metadataState.map { it.searchTermsSong }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val _editState = MutableStateFlow(LibraryEditState())
    val songToEdit: StateFlow<Song?> = _editState.map { it.song }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val editError: StateFlow<UiText?> = _editState.map { it.error }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val needsStoragePermission: StateFlow<Boolean> = _editState.map { it.needsStoragePermission }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val needsShizukuPermission: StateFlow<Boolean> = _editState.map { it.needsShizukuPermission }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val isSavingMetadata: StateFlow<Boolean> = _editState.map { it.isSaving }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    // ========== Batch Selection Mode ==========
    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    private val _selectedSongIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedSongIds: StateFlow<Set<Long>> = _selectedSongIds.asStateFlow()

    val selectedSongCount: StateFlow<Int> = _selectedSongIds.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun enterSelectionMode() {
        _isSelectionMode.value = true
    }

    fun exitSelectionMode() {
        _isSelectionMode.value = false
        _selectedSongIds.value = emptySet()
    }

    fun toggleSongSelection(songId: Long) {
        val currentSelection = _selectedSongIds.value.toMutableSet()
        if (currentSelection.contains(songId)) {
            currentSelection.remove(songId)
        } else {
            currentSelection.add(songId)
        }
        _selectedSongIds.value = currentSelection

        // Exit selection mode if no songs selected
        if (currentSelection.isEmpty()) {
            _isSelectionMode.value = false
        }
    }

    fun selectAllSongs() {
        _selectedSongIds.value = songs.value.map { it.id }.toSet()
        if (_selectedSongIds.value.isNotEmpty()) {
            _isSelectionMode.value = true
        }
    }

    fun isSongSelected(songId: Long): Boolean {
        return _selectedSongIds.value.contains(songId)
    }

    init {
        refreshStats()
        viewModelScope.launch {
            artworkRepository.coverGenerationCompleted.collect {
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
        // 中文按拼音首字母参与排序（先按拼音键，相同再按原始值稳定排序）
        return when (type) {
            SortType.TITLE_ASC -> songs.sortedWith(compareBy<Song>({ Pinyin.sortKey(it.title) }, { it.title }))
            SortType.TITLE_DESC -> songs.sortedWith(compareBy<Song>({ Pinyin.sortKey(it.title) }, { it.title }).reversed())
            SortType.ARTIST_ASC -> songs.sortedWith(compareBy<Song>({ Pinyin.sortKey(it.artist) }, { it.artist }))
            SortType.ARTIST_DESC -> songs.sortedWith(compareBy<Song>({ Pinyin.sortKey(it.artist) }, { it.artist }).reversed())
            SortType.ALBUM_ASC -> songs.sortedWith(compareBy<Song>({ Pinyin.sortKey(it.album) }, { it.album }))
            SortType.ALBUM_DESC -> songs.sortedWith(compareBy<Song>({ Pinyin.sortKey(it.album) }, { it.album }).reversed())
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
        playbackActionController.playSongAtIndex(songList, startIndex)
    }

    fun playShuffled(songList: List<Song>) {
        if (songList.isEmpty()) return
        playbackActionController.playShuffled(songList)
    }

    fun playAll(startIndex: Int = 0) {
        val list = if (_searchState.value.query.isNotBlank()) _searchState.value.results else songs.value
        if (list.isNotEmpty()) playSongs(list, startIndex)
    }

    fun getSongsByAlbum(album: String, callback: (List<Song>) -> Unit) {
        viewModelScope.launch {
            callback(getSongsByAlbumUseCase(album))
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
            callback(getSongsByArtistUseCase(artist))
        }
    }

    fun requestDeleteSong(song: Song) {
        _songToDelete.value = song
        _deleteError.value = null
    }
    fun cancelDelete() {
        _songToDelete.value = null
        _deleteError.value = null
    }
    fun confirmDelete() {
        val song = _songToDelete.value ?: return
        viewModelScope.launch {
            try {
                when (val result = deleteSongUseCase(song)) {
                    is OperationResult.Success -> {
                        _songToDelete.value = null
                        _deleteError.value = null
                        refreshStats()
                    }
                    is OperationResult.Failure -> {
                        _deleteError.value = result.toUiText()
                        showPermissionRecoveryIfNeeded(result)
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                MuseLog.e("LibraryViewModel", "Delete failed unexpectedly", e)
                _deleteError.value = OperationResult.Failure(OperationError.UNKNOWN, e.message).toUiText()
            }
        }
    }

    fun requestRenameSong(song: Song) {
        requestEditMetadata(song)
    }

    fun generateDefaultCoverPreview(title: String): ByteArray? {
        return when (val result = artworkRepository.generateDefaultCoverPreview(title)) {
            is OperationResult.Success -> result.value
            is OperationResult.Failure -> null
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
            isFetching = true,
            isApplying = false
        )
        viewModelScope.launch {
            try {
                val results = searchMetadataUseCase.exact(
                    title = title,
                    artist = artist.ifBlank { null }
                )
                _metadataState.value = _metadataState.value.copy(results = results)
                if (results.isEmpty()) {
                    _metadataState.value = _metadataState.value.copy(error = UiText.Resource(R.string.metadata_no_results))
                }
            } catch (e: java.io.IOException) {
                MuseLog.w("LibraryViewModel", "Exact metadata search failed", e)
                _metadataState.value = _metadataState.value.copy(error = UiText.Resource(R.string.error_network))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: IllegalStateException) {
                MuseLog.w("LibraryViewModel", "Exact metadata search returned invalid state", e)
                _metadataState.value = _metadataState.value.copy(error = UiText.Resource(R.string.error_unknown))
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
            isFetching = true,
            isApplying = false
        )
        viewModelScope.launch {
            try {
                val results = searchMetadataUseCase(song.title, song.artist)
                _metadataState.value = _metadataState.value.copy(results = results)
                if (results.isEmpty()) {
                    _metadataState.value = _metadataState.value.copy(error = UiText.Resource(R.string.metadata_no_results))
                }
            } catch (e: java.io.IOException) {
                MuseLog.w("LibraryViewModel", "Metadata search failed", e)
                _metadataState.value = _metadataState.value.copy(error = UiText.Resource(R.string.error_network))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: IllegalStateException) {
                MuseLog.w("LibraryViewModel", "Metadata search returned invalid state", e)
                _metadataState.value = _metadataState.value.copy(error = UiText.Resource(R.string.error_unknown))
            } finally {
                _metadataState.value = _metadataState.value.copy(isFetching = false)
            }
        }
    }

    fun applyMetadataResult(result: MetadataResult) {
        val state = _metadataState.value
        if (state.isFetching || state.isApplying) return
        val song = state.song ?: return
        if (!hasFullFileAccess() && !shizukuPermissionController.isGranted()) {
            if (shizukuPermissionController.isAvailable()) {
                _editState.value = _editState.value.copy(needsShizukuPermission = true)
            } else {
                _editState.value = _editState.value.copy(needsStoragePermission = true)
            }
            return
        }
        _metadataState.value = _metadataState.value.copy(isApplying = true, error = null)
        viewModelScope.launch {
            try {
                val updatedSong = when (val metadataResult = applyMetadataUseCase(song, result)) {
                    is OperationResult.Success -> metadataResult.value
                    is OperationResult.Failure -> {
                        showMetadataFailure(metadataResult)
                        return@launch
                    }
                }
                deleteLyricsUseCase(song.id)
                clearLyricsCacheUseCase()
                val coverUrl = result.coverUrl
                if (!coverUrl.isNullOrBlank()) {
                    when (val downloadResult = artworkRepository.downloadBytes(coverUrl)) {
                        is OperationResult.Success -> {
                            when (val artworkResult = updateSongArtworkUseCase(updatedSong, downloadResult.value)) {
                                is OperationResult.Success -> Unit
                                is OperationResult.Failure -> {
                                    showMetadataFailure(artworkResult)
                                    return@launch
                                }
                            }
                        }
                        is OperationResult.Failure -> {
                            showMetadataFailure(downloadResult)
                            return@launch
                        }
                    }
                }
                refreshAlbumAndArtistTablesUseCase()
                closeMetadataSheet()
            } catch (e: java.io.IOException) {
                MuseLog.w("LibraryViewModel", "Metadata artwork download failed", e)
                _metadataState.value = _metadataState.value.copy(error = UiText.Resource(R.string.error_network))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: android.database.sqlite.SQLiteException) {
                MuseLog.e("LibraryViewModel", "Metadata database update failed", e)
                _metadataState.value = _metadataState.value.copy(error = UiText.Resource(R.string.error_database))
            } catch (e: OutOfMemoryError) {
                MuseLog.e("LibraryViewModel", "Metadata apply ran out of memory", e)
                Runtime.getRuntime().gc()
                _metadataState.value = _metadataState.value.copy(error = UiText.Resource(R.string.error_unknown))
            } catch (e: Exception) {
                MuseLog.e("LibraryViewModel", "Metadata apply failed unexpectedly", e)
                _metadataState.value = _metadataState.value.copy(error = UiText.Resource(R.string.error_unknown))
            } finally {
                if (_metadataState.value.song?.id == song.id) {
                    _metadataState.value = _metadataState.value.copy(isApplying = false)
                }
            }
        }
    }

    fun closeMetadataSheet() {
        _metadataState.value = LibraryMetadataState()
    }

    private fun hasFullFileAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            storagePermissionController.hasFullFileAccess()
        } else {
            true
        }
    }

    fun requestStoragePermission() {
        storagePermissionController.requestFullFileAccess()
        _editState.value = _editState.value.copy(needsStoragePermission = false)
    }

    fun requestEditMetadata(song: Song) {
        if (!hasFullFileAccess() && !shizukuPermissionController.isGranted()) {
            if (shizukuPermissionController.isAvailable()) {
                _editState.value = _editState.value.copy(needsShizukuPermission = true)
            } else {
                _editState.value = _editState.value.copy(needsStoragePermission = true)
            }
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
            _editState.value = _editState.value.copy(error = UiText.Resource(R.string.song_name_empty))
            return
        }
        val year = yearStr.toIntOrNull()
        _editState.value = _editState.value.copy(error = null, isSaving = true)
        viewModelScope.launch {
            try {
                val simpleTitle = textNormalizer.toSimplified(title)
                val simpleArtist = textNormalizer.toSimplified(artist)
                val simpleAlbum = textNormalizer.toSimplified(album)
                val simpleGenre = textNormalizer.toSimplified(genre)

                when (
                    val editResult = editSongMetadataUseCase(
                        song = song,
                        title = simpleTitle,
                        artist = simpleArtist,
                        album = simpleAlbum,
                        year = year,
                        genre = simpleGenre
                    )
                ) {
                    is OperationResult.Success -> Unit
                    is OperationResult.Failure -> {
                        showEditFailure(editResult)
                        return@launch
                    }
                }
                if (simpleTitle != song.title || simpleArtist != song.artist || simpleAlbum != song.album) {
                    deleteLyricsUseCase(song.id)
                    clearLyricsCacheUseCase()
                }
                if (artworkBytes != null) {
                    val artworkTarget = findCurrentSong(song.id) ?: song
                    when (val artworkResult = updateSongArtworkUseCase(artworkTarget, artworkBytes)) {
                        is OperationResult.Success -> Unit
                        is OperationResult.Failure -> {
                            showEditFailure(artworkResult)
                            return@launch
                        }
                    }
                }
                refreshAlbumAndArtistTablesUseCase()
                refreshStats()
                _editState.value = LibraryEditState()
            } catch (e: java.io.IOException) {
                MuseLog.w("LibraryViewModel", "Metadata edit IO failed", e)
                showEditFailure(OperationResult.Failure(OperationError.IO, e.message))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: android.database.sqlite.SQLiteException) {
                MuseLog.e("LibraryViewModel", "Metadata edit database failed", e)
                showEditFailure(OperationResult.Failure(OperationError.DATABASE, e.message))
            } catch (e: SecurityException) {
                MuseLog.e("LibraryViewModel", "Metadata edit permission denied", e)
                showEditFailure(OperationResult.Failure(OperationError.PERMISSION_DENIED, e.message))
            } catch (e: OutOfMemoryError) {
                MuseLog.e("LibraryViewModel", "Metadata edit ran out of memory", e)
                Runtime.getRuntime().gc()
                showEditFailure(OperationResult.Failure(OperationError.UNKNOWN, e.message))
            } catch (e: Exception) {
                MuseLog.e("LibraryViewModel", "Metadata edit failed unexpectedly", e)
                showEditFailure(OperationResult.Failure(OperationError.UNKNOWN, e.message))
            } finally {
                if (_editState.value.song?.id == song.id) {
                    _editState.value = _editState.value.copy(isSaving = false)
                }
            }
        }
    }

    private fun showEditFailure(failure: OperationResult.Failure) {
        _editState.value = _editState.value.copy(error = failure.toUiText())
        showPermissionRecoveryIfNeeded(failure)
    }

    private fun showMetadataFailure(failure: OperationResult.Failure) {
        _metadataState.value = _metadataState.value.copy(error = failure.toUiText())
        showPermissionRecoveryIfNeeded(failure)
    }

    private fun showPermissionRecoveryIfNeeded(failure: OperationResult.Failure) {
        if (failure.error == OperationError.PERMISSION_DENIED) {
            val shizukuAvailable = shizukuPermissionController.isAvailable()
            _editState.value = _editState.value.copy(
                needsStoragePermission = !shizukuAvailable,
                needsShizukuPermission = shizukuAvailable
            )
        }
    }

    fun requestShizukuPermission() {
        shizukuPermissionController.requestGrant()
    }

    fun dismissShizukuPermissionDialog() {
        _editState.value = _editState.value.copy(needsShizukuPermission = false)
    }

    private fun findCurrentSong(songId: Long): Song? {
        return songRepository.songs.value.find { it.id == songId }
            ?: songRepository.audiobooks.value.find { it.id == songId }
    }

    // ========== Playlist Operations ==========
    fun requestAddToPlaylist(song: Song) {
        _songToAddToPlaylist.value = song
    }

    fun cancelAddToPlaylist() {
        _songToAddToPlaylist.value = null
    }

    fun addSongToPlaylist(playlistId: Long) {
        val song = _songToAddToPlaylist.value ?: return
        viewModelScope.launch {
            try {
                playlistRepository.addSongToPlaylist(playlistId, song.id)
                _songToAddToPlaylist.value = null
            } catch (e: Exception) {
                // Handle error silently or show a toast
            }
        }
    }

    fun addSelectedSongsToPlaylist(playlistId: Long) {
        val selectedIds = _selectedSongIds.value.toList()
        if (selectedIds.isEmpty()) return

        viewModelScope.launch {
            try {
                playlistRepository.addSongsToPlaylist(playlistId, selectedIds)
                // Clear selection after adding
                exitSelectionMode()
                // Close the add-to-playlist dialog
                _songToAddToPlaylist.value = null
            } catch (e: Exception) {
                MuseLog.e("LibraryViewModel", "Failed to add songs to playlist", e)
            }
        }
    }

    fun requestAddSelectedToPlaylist() {
        if (_selectedSongIds.value.isEmpty()) return
        // Using a placeholder song to trigger the dialog - the actual songs are in _selectedSongIds
        _songToAddToPlaylist.value = songs.value.find { it.id == _selectedSongIds.value.first() }
    }
}
