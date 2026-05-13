package luzzr.muse.ui.screens.library

import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import luzzr.muse.MuseApp
import luzzr.muse.data.model.Album
import luzzr.muse.data.model.Artist
import luzzr.muse.data.model.Song
import luzzr.muse.data.model.SortType
import luzzr.muse.data.network.MetadataFetcher
import luzzr.muse.data.network.MetadataResult
import luzzr.muse.data.repository.MusicRepository
import luzzr.muse.player.MusicService
import luzzr.muse.player.PlayerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: MusicRepository = (application as MuseApp).repository
    private val playerState: PlayerState = (application as MuseApp).playerState

    private val _sortType = MutableStateFlow(SortType.TITLE_ASC)
    val sortType: StateFlow<SortType> = _sortType.asStateFlow()

    val songs: StateFlow<List<Song>> = combine(
        repository.songs,
        _sortType
    ) { allSongs, sort ->
        if (allSongs.isEmpty()) allSongs
        else sortSongs(allSongs, sort)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val isScanning: StateFlow<Boolean> = repository.isScanning

    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums.asStateFlow()

    private val _artists = MutableStateFlow<List<Artist>>(emptyList())
    val artists: StateFlow<List<Artist>> = _artists.asStateFlow()

    /** Album song list displayed in a BottomSheet */
    private val _albumDetail = MutableStateFlow<Pair<Album, List<Song>>?>(null)
    val albumDetail: StateFlow<Pair<Album, List<Song>>?> = _albumDetail.asStateFlow()

    /** Artist song list displayed in a BottomSheet */
    private val _artistDetail = MutableStateFlow<Pair<Artist, List<Song>>?>(null)
    val artistDetail: StateFlow<Pair<Artist, List<Song>>?> = _artistDetail.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Song>>(emptyList())
    val searchResults: StateFlow<List<Song>> = _searchResults.asStateFlow()

    private val _songToDelete = MutableStateFlow<Song?>(null)
    val songToDelete: StateFlow<Song?> = _songToDelete.asStateFlow()

    private val _songToRename = MutableStateFlow<Song?>(null)
    val songToRename: StateFlow<Song?> = _songToRename.asStateFlow()

    // --- Metadata fetch state ---
    private val _metadataSong = MutableStateFlow<Song?>(null)
    val metadataSong: StateFlow<Song?> = _metadataSong.asStateFlow()

    private val _metadataResults = MutableStateFlow<List<MetadataResult>>(emptyList())
    val metadataResults: StateFlow<List<MetadataResult>> = _metadataResults.asStateFlow()

    private val _metadataLoading = MutableStateFlow(false)
    val metadataLoading: StateFlow<Boolean> = _metadataLoading.asStateFlow()

    private val _metadataError = MutableStateFlow<String?>(null)
    val metadataError: StateFlow<String?> = _metadataError.asStateFlow()

    init {
        refreshStats()

        // Refresh album/artist lists when batch cover generation completes
        viewModelScope.launch {
            repository.coverGenerationCompleted.collect {
                refreshStats()
            }
        }
    }

    fun setSortType(type: SortType) {
        _sortType.value = type
    }

    /** Cycle to next sort type in the predefined order */
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
            _albums.value = repository.getAlbums()
            _artists.value = repository.getArtists()
        }
    }

    fun search(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            _searchResults.value = repository.search(query)
        }
    }

    fun playSongs(songList: List<Song>, startIndex: Int = 0) {
        val ctx = getApplication<Application>()
        ctx.startForegroundService(Intent(ctx, MusicService::class.java))
        playerState.playSongs(songList, startIndex)
    }

    /** Play list shuffled — random start + enable shuffle mode */
    fun playShuffled(songList: List<Song>) {
        if (songList.isEmpty()) return
        val ctx = getApplication<Application>()
        ctx.startForegroundService(Intent(ctx, MusicService::class.java))
        playerState.playShuffled(songList)
    }

    fun playAll(startIndex: Int = 0) {
        val list = if (_searchQuery.value.isNotBlank()) _searchResults.value else songs.value
        if (list.isNotEmpty()) playSongs(list, startIndex)
    }

    fun getSongsByAlbum(album: String, callback: (List<Song>) -> Unit) {
        viewModelScope.launch {
            val songs = repository.getSongsByAlbum(album)
            withContext(Dispatchers.Main) { callback(songs) }
        }
    }

    /** Show album song list in a BottomSheet */
    fun showAlbumSongs(album: Album) {
        viewModelScope.launch {
            val songs = repository.getSongsByAlbum(album.title)
            _albumDetail.value = album to songs
        }
    }

    /** Dismiss album detail sheet */
    fun dismissAlbumDetail() { _albumDetail.value = null }

    /** Show artist song list in a BottomSheet */
    fun showArtistSongs(artist: Artist) {
        viewModelScope.launch {
            val songs = repository.getSongsByArtist(artist.name)
            _artistDetail.value = artist to songs
        }
    }

    /** Dismiss artist detail sheet */
    fun dismissArtistDetail() { _artistDetail.value = null }

    fun getSongsByArtist(artist: String, callback: (List<Song>) -> Unit) {
        viewModelScope.launch {
            val songs = repository.getSongsByArtist(artist)
            withContext(Dispatchers.Main) { callback(songs) }
        }
    }

    fun requestDeleteSong(song: Song) { _songToDelete.value = song }
    fun cancelDelete() { _songToDelete.value = null }
    fun confirmDelete() {
        val song = _songToDelete.value ?: return
        viewModelScope.launch {
            repository.deleteSong(song)
            _songToDelete.value = null
            refreshStats()
        }
    }

    fun requestRenameSong(song: Song) { _songToRename.value = song }
    fun cancelRename() { _songToRename.value = null }
    fun confirmRename(newTitle: String) {
        val song = _songToRename.value ?: return
        viewModelScope.launch {
            repository.renameSong(song, newTitle)
            _songToRename.value = null
        }
    }

    // --- Metadata fetch ---

    private val _showSearchTermsDialog = MutableStateFlow<Song?>(null)
    val showSearchTermsDialog: StateFlow<Song?> = _showSearchTermsDialog.asStateFlow()

    /** Show the search terms editor dialog before fetching */
    fun requestSearchMetadata(song: Song) {
        _showSearchTermsDialog.value = song
    }

    fun cancelSearchTerms() { _showSearchTermsDialog.value = null }

    /** Start searching with user-provided exact terms */
    fun searchMetadataExact(title: String, artist: String) {
        val song = _showSearchTermsDialog.value ?: return
        _showSearchTermsDialog.value = null
        _metadataSong.value = song
        _metadataResults.value = emptyList()
        _metadataError.value = null
        _metadataLoading.value = true
        viewModelScope.launch {
            try {
                val fetcher = MetadataFetcher.getInstance()
                val results = fetcher.searchExact(
                    title = title,
                    artist = artist.ifBlank { null }
                )
                _metadataResults.value = results
                if (results.isEmpty()) {
                    _metadataError.value = "未找到匹配结果，请尝试修改搜索关键词后重试"
                }
            } catch (e: Exception) {
                _metadataError.value = "网络请求失败: ${e.localizedMessage ?: "未知错误"}"
            } finally {
                _metadataLoading.value = false
            }
        }
    }

    /** Start searching for metadata for the given song. Opens the results sheet. */
    fun searchMetadata(song: Song) {
        _metadataSong.value = song
        _metadataResults.value = emptyList()
        _metadataError.value = null
        _metadataLoading.value = true
        viewModelScope.launch {
            try {
                val fetcher = MetadataFetcher.getInstance()
                val results = fetcher.search(
                    rawTitle = song.title,
                    rawArtist = if (song.artist != "Unknown Artist") song.artist else null
                )
                _metadataResults.value = results
                if (results.isEmpty()) {
                    _metadataError.value = "未找到匹配结果，请尝试修改搜索关键词后重试"
                }
            } catch (e: Exception) {
                _metadataError.value = "网络请求失败: ${e.localizedMessage ?: "未知错误"}"
            } finally {
                _metadataLoading.value = false
            }
        }
    }

    /** Apply a selected metadata result to the song. */
    fun applyMetadataResult(result: MetadataResult) {
        val song = _metadataSong.value ?: return
        _metadataLoading.value = true
        viewModelScope.launch {
            try {
                // Update metadata tags in DB
                repository.updateSongWithMetadata(song, result)
                // Download and save cover art if available
                if (!result.coverUrl.isNullOrBlank()) {
                    val bytes = repository.downloadBytes(result.coverUrl)
                    if (bytes != null) {
                        repository.updateSongArtwork(song, bytes)
                    }
                }
                // Refresh album/artist tables
                repository.refreshAlbumAndArtistTables()
                closeMetadataSheet()
            } catch (e: Exception) {
                _metadataError.value = "保存失败: ${e.localizedMessage ?: "未知错误"}"
            } finally {
                _metadataLoading.value = false
            }
        }
    }

    /** Close metadata result sheet. */
    fun closeMetadataSheet() {
        _metadataSong.value = null
        _metadataResults.value = emptyList()
        _metadataError.value = null
        _metadataLoading.value = false
    }

    // --- Metadata editor ---

    private val _songToEdit = MutableStateFlow<Song?>(null)
    val songToEdit: StateFlow<Song?> = _songToEdit.asStateFlow()

    private val _editError = MutableStateFlow<String?>(null)
    val editError: StateFlow<String?> = _editError.asStateFlow()

    private val _needsStoragePermission = MutableStateFlow(false)
    val needsStoragePermission: StateFlow<Boolean> = _needsStoragePermission.asStateFlow()

    /** Check if we have MANAGE_EXTERNAL_STORAGE on Android 11+ */
    fun hasFullFileAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            // On Android 10 and below, regular storage permissions suffice
            true
        }
    }

    /** Open system settings for MANAGE_EXTERNAL_STORAGE */
    fun requestStoragePermission() {
        val ctx = getApplication<Application>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = android.net.Uri.parse("package:${ctx.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
        }
        _needsStoragePermission.value = false
    }

    fun requestEditMetadata(song: Song) {
        if (!hasFullFileAccess()) {
            _needsStoragePermission.value = true
            return
        }
        _songToEdit.value = song
        _editError.value = null
    }

    fun dismissPermissionDialog() { _needsStoragePermission.value = false }
    fun cancelEditMetadata() { _songToEdit.value = null; _editError.value = null }

    fun saveEditedMetadata(
        title: String, artist: String, album: String, yearStr: String, genre: String,
        artworkBytes: ByteArray? = null
    ) {
        val song = _songToEdit.value ?: return
        if (title.isBlank()) { _editError.value = "歌曲名不能为空"; return }
        val year = yearStr.toIntOrNull()
        _songToEdit.value = null
        viewModelScope.launch {
            // Convert Traditional Chinese to Simplified Chinese for consistency
            val converter = luzzr.muse.data.network.MetadataResult.Companion::toSimplifiedText
            val simpleTitle = converter(title)
            val simpleArtist = converter(artist)
            val simpleAlbum = converter(album)
            val simpleGenre = converter(genre)

            // Save metadata tags
            repository.updateSongTags(
                song = song, title = simpleTitle, artist = simpleArtist, album = simpleAlbum,
                year = year, genre = simpleGenre
            )
            // Save artwork if provided
            if (artworkBytes != null) {
                repository.updateSongArtwork(song, artworkBytes)
            }
            // Refresh album/artist tables since metadata changed
            repository.refreshAlbumAndArtistTables()
            refreshStats()
        }
    }
}
