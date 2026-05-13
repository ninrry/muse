package luzzr.muse.ui.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import luzzr.muse.data.model.Album
import luzzr.muse.data.model.Artist
import luzzr.muse.data.model.Song
import luzzr.muse.data.model.SortType
import luzzr.muse.data.network.MetadataResult
import luzzr.muse.ui.animation.MotionDuration
import luzzr.muse.ui.components.AlbumArtThumbnail
import luzzr.muse.ui.components.FormatBadge
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.OutlinedButton
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel = viewModel(),
    innerPadding: PaddingValues = PaddingValues()
) {
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    val currentSortType by viewModel.sortType.collectAsStateWithLifecycle()
    val metadataResults by viewModel.metadataResults.collectAsStateWithLifecycle()
    val metadataLoading by viewModel.metadataLoading.collectAsStateWithLifecycle()
    val metadataError by viewModel.metadataError.collectAsStateWithLifecycle()
    val metadataSong by viewModel.metadataSong.collectAsStateWithLifecycle()
    val showSearchTerms by viewModel.showSearchTermsDialog.collectAsStateWithLifecycle()
    val albumDetail by viewModel.albumDetail.collectAsStateWithLifecycle()
    val artistDetail by viewModel.artistDetail.collectAsStateWithLifecycle()

    var subTab by remember { mutableIntStateOf(0) }
    val subTabs = listOf("歌曲", "专辑", "艺术家")
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("曲库") },
                actions = {
                    // Sort button — cycles through sort types
                    IconButton(onClick = { viewModel.cycleSortType() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.Sort,
                            contentDescription = "排序"
                        )
                    }
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(Icons.Default.Search, contentDescription = "搜索")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            // MD3 Segmented Button per spec
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
            ) {
                subTabs.forEachIndexed { index, label ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = subTabs.size),
                        onClick = {
                            subTab = index
                            if (index == 1 || index == 2) viewModel.refreshStats()
                        },
                        selected = subTab == index
                    ) {
                        Text(label)
                    }
                }
            }

            // Current sort indicator (shown when in Song tab)
            if (subTab == 0) {
                Text(
                    text = currentSortType.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, bottom = 4.dp)
                )
            }

            // Search bar per spec (capsule, real-time search) — animated
            AnimatedVisibility(
                visible = showSearch,
                enter = expandVertically(animationSpec = tween(MotionDuration.medium1)) + fadeIn(animationSpec = tween(MotionDuration.medium1)),
                exit = shrinkVertically(animationSpec = tween(MotionDuration.short)) + fadeOut(animationSpec = tween(MotionDuration.short))
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        if (subTab == 0) viewModel.search(it)
                    },
                    placeholder = { Text("搜索歌曲、专辑、艺术家…") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(28.dp)
                )
            }

            when (subTab) {
                0 -> SongListView(songs, viewModel, searchQuery)
                1 -> AlbumGridView(albums, viewModel)
                2 -> ArtistListView(artists, viewModel)
            }
            // Bottom spacer to clear MiniPlayer
            Spacer(Modifier.height(88.dp))
        }
    }

    // Delete dialog
    val songToDelete by viewModel.songToDelete.collectAsStateWithLifecycle()
    songToDelete?.let { song ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelDelete() },
            title = { Text("删除歌曲") },
            text = { Text("确定要删除「${song.title}」吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmDelete() }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDelete() }) { Text("取消") }
            }
        )
    }

    // Rename dialog — now redirects to metadata editor
    val songToRename by viewModel.songToRename.collectAsStateWithLifecycle()
    songToRename?.let { song -> viewModel.requestEditMetadata(song); viewModel.cancelRename() }

    // Full metadata editor dialog
    val songToEdit by viewModel.songToEdit.collectAsStateWithLifecycle()
    val editError by viewModel.editError.collectAsStateWithLifecycle()
    val needsStoragePermission by viewModel.needsStoragePermission.collectAsStateWithLifecycle()
    songToEdit?.let { song ->
        var editTitle by remember(song.id) { mutableStateOf(song.title) }
        var editArtist by remember(song.id) { mutableStateOf(song.artist) }
        var editAlbum by remember(song.id) { mutableStateOf(song.album) }
        var editYear by remember(song.id) { mutableStateOf(song.year?.toString() ?: "") }
        var editGenre by remember(song.id) { mutableStateOf(song.genre) }
        var selectedArtworkBytes by remember(song.id) { mutableStateOf<ByteArray?>(null) }
        var selectedArtworkUri by remember(song.id) { mutableStateOf<android.net.Uri?>(null) }

        // Photo picker
        val context = LocalContext.current
        val artworkPicker = rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
        ) { uri: android.net.Uri? ->
            if (uri != null) {
                selectedArtworkUri = uri
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    selectedArtworkBytes = inputStream?.readBytes()
                    inputStream?.close()
                } catch (_: Exception) { }
            }
        }

        AlertDialog(
            onDismissRequest = { viewModel.cancelEditMetadata() },
            title = { Text("编辑元数据") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Artwork section
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Artwork preview
                        Surface(
                            modifier = Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            tonalElevation = 2.dp
                        ) {
                            if (selectedArtworkUri != null) {
                                coil.compose.AsyncImage(
                                    model = selectedArtworkUri,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else if (song.artworkUri != null) {
                                coil.compose.AsyncImage(
                                    model = song.artworkUri,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Image,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Column {
                            OutlinedButton(onClick = { artworkPicker.launch("image/*") }) {
                                Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("选择封面", style = MaterialTheme.typography.labelMedium)
                            }
                            Spacer(Modifier.height(4.dp))
                            // Generate default cover button
                            TextButton(
                                onClick = {
                                    val defaultBytes = luzzr.muse.data.tag.DefaultCoverGenerator.generate(song.title)
                                    selectedArtworkBytes = defaultBytes
                                    // Create a temp file URI for preview
                                    try {
                                        val cacheDir = context.cacheDir
                                        // Delete old preview file first to avoid Coil cache
                                        val oldFile = java.io.File(cacheDir, "cover_preview_${song.id}.png")
                                        oldFile.delete()
                                        val tempFile = java.io.File(cacheDir, "cover_preview_${song.id}_${System.nanoTime()}.png")
                                        tempFile.outputStream().use { it.write(defaultBytes) }
                                        selectedArtworkUri = android.net.Uri.fromFile(tempFile)
                                    } catch (_: Exception) { }
                                }
                            ) {
                                Text("生成默认封面", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        singleLine = true,
                        label = { Text("歌曲名 *") }
                    )
                    OutlinedTextField(
                        value = editArtist,
                        onValueChange = { editArtist = it },
                        singleLine = true,
                        label = { Text("艺术家") }
                    )
                    OutlinedTextField(
                        value = editAlbum,
                        onValueChange = { editAlbum = it },
                        singleLine = true,
                        label = { Text("专辑") }
                    )
                    OutlinedTextField(
                        value = editYear,
                        onValueChange = { editYear = it },
                        singleLine = true,
                        label = { Text("年代") },
                        placeholder = { Text("如: 2024") }
                    )
                    OutlinedTextField(
                        value = editGenre,
                        onValueChange = { editGenre = it },
                        singleLine = true,
                        label = { Text("流派") },
                        placeholder = { Text("如: Pop") }
                    )
                    editError?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.saveEditedMetadata(
                            title = editTitle, artist = editArtist, album = editAlbum,
                            yearStr = editYear, genre = editGenre,
                            artworkBytes = selectedArtworkBytes
                        )
                    },
                    enabled = editTitle.isNotBlank()
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelEditMetadata() }) { Text("取消") }
            }
        )
    }

    // MANAGE_EXTERNAL_STORAGE permission guide dialog
    if (needsStoragePermission) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissPermissionDialog() },
            title = { Text("需要存储访问权限") },
            text = {
                Text(
                    "编辑歌曲元数据（标题、艺术家、专辑等）需要将修改写入音频文件。\\n\\n" +
                    "Android 11 及以上系统要求应用获得「管理所有文件」权限才能修改音乐文件。\\n\\n" +
                    "请点击「前往设置」，然后在设置中开启「允许管理所有文件」。"
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.requestStoragePermission() }) {
                    Text("前往设置")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissPermissionDialog() }) {
                    Text("取消")
                }
            }
        )
    }

    // Search terms editor (before metadata fetch)
    showSearchTerms?.let { song ->
        SearchTermsDialog(
            song = song,
            onConfirm = { title, artist ->
                viewModel.searchMetadataExact(title, artist)
            },
            onDismiss = { viewModel.cancelSearchTerms() }
        )
    }

    // Metadata results bottom sheet
    if (metadataSong != null) {
        MetadataResultSheet(
            song = metadataSong!!,
            results = metadataResults,
            isLoading = metadataLoading,
            error = metadataError,
            onApply = { viewModel.applyMetadataResult(it) },
            onDismiss = { viewModel.closeMetadataSheet() }
        )
    }

    // Album song list bottom sheet
    albumDetail?.let { (album, songs) ->
        SongListSheet(
            title = "专辑: ${album.title}",
            subtitle = "${songs.size}首歌曲",
            songs = songs,
            onSongClick = { index -> viewModel.playSongs(songs, index) },
            onDismiss = { viewModel.dismissAlbumDetail() }
        )
    }

    // Artist song list bottom sheet
    artistDetail?.let { (artist, songs) ->
        SongListSheet(
            title = "艺术家: ${artist.name}",
            subtitle = "${songs.size}首歌曲",
            songs = songs,
            onSongClick = { index -> viewModel.playSongs(songs, index) },
            onDismiss = { viewModel.dismissArtistDetail() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MetadataResultSheet(
    song: Song,
    results: List<MetadataResult>,
    isLoading: Boolean,
    error: String?,
    onApply: (MetadataResult) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                "网络搜索元数据",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "正在搜索: ${song.title}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("正在搜索…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else if (error != null && results.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 600.dp)
                ) {
                    itemsIndexed(results, key = { i, _ -> i }) { _, result ->
                        MetadataResultItem(
                            result = result,
                            onApply = { onApply(result) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetadataResultItem(
    result: MetadataResult,
    onApply: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(48.dp)) {
                AlbumArtThumbnail(
                    artworkUri = result.coverUrl?.let { android.net.Uri.parse(it) },
                    placeholder = result.title.take(1).uppercase(),
                    modifier = Modifier.size(48.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    result.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    result.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (result.album.isNotBlank()) {
                        Text(
                            result.album,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                    result.year?.let {
                        Text(
                            "$it",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    Text(
                        result.source,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onApply,
                modifier = Modifier.height(36.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text("应用", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
fun SongListView(songs: List<Song>, viewModel: LibraryViewModel, searchQuery: String = "") {
    if (songs.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无歌曲，请先扫描音乐", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Shuffle play button at top
        item(key = "shuffle_header") {
            ElevatedCard(
                onClick = { viewModel.playShuffled(songs) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Shuffle,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "随机播放全部 (${songs.size}首)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
            LibrarySongItem(
                song = song,
                onClick = { viewModel.playSongs(songs, index) },
                onDelete = { viewModel.requestDeleteSong(song) },
                onRename = { viewModel.requestRenameSong(song) },
                onSearchMetadata = { viewModel.requestSearchMetadata(song) }
            )
        }
        // Bottom spacer to clear MiniPlayer (~88dp)
        item { Spacer(Modifier.height(88.dp)) }
    }
}

@Composable
fun LibrarySongItem(
    song: Song,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onSearchMetadata: () -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(36.dp)) {
                    AlbumArtThumbnail(
                        artworkUri = song.artworkUri,
                        placeholder = song.title.take(1).uppercase(),
                        modifier = Modifier.size(36.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        song.title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            song.artist,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        song.codec?.let { codec ->
                            FormatBadge(text = codec)
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    song.formattedDuration,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )

                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "更多")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("网络搜索元数据") },
                            onClick = { showMenu = false; onSearchMetadata() },
                            leadingIcon = { Icon(Icons.Default.CloudDownload, null) }
                        )
                        DropdownMenuItem(
                            text = { Text("编辑元数据") },
                            onClick = { showMenu = false; onRename() },
                            leadingIcon = { Icon(Icons.Default.Edit, null) }
                        )
                        DropdownMenuItem(
                            text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                            onClick = { showMenu = false; onDelete() },
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                        )
                    }
                }
            }
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )
        }
    }
}

@Composable
fun AlbumGridView(albums: List<Album>, viewModel: LibraryViewModel) {
    if (albums.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无专辑数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(albums, key = { _, album -> album.id }) { _, album ->
            ElevatedCard(
                onClick = { viewModel.showAlbumSongs(album) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AlbumArtThumbnail(
                        artworkUri = album.artworkUri,
                        placeholder = album.title.take(1).uppercase(),
                        modifier = Modifier.size(120.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        album.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${album.artist} · ${album.songCount}首",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
    // Bottom spacer to clear MiniPlayer
    Spacer(Modifier.height(88.dp))
}

@Composable
fun ArtistListView(artists: List<Artist>, viewModel: LibraryViewModel) {
    if (artists.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无艺术家数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        itemsIndexed(artists, key = { _, artist -> artist.name }) { _, artist ->
            ElevatedCard(
                onClick = { viewModel.showArtistSongs(artist) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(artist.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${artist.songCount}首 · ${artist.albumCount}张专辑",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
    // Bottom spacer to clear MiniPlayer
    Spacer(Modifier.height(88.dp))
}

/**
 * A reusable ModalBottomSheet that displays a list of songs.
 * Used for album and artist detail views.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SongListSheet(
    title: String,
    subtitle: String,
    songs: List<Song>,
    onSongClick: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.heightIn(max = 500.dp)
            ) {
                itemsIndexed(songs, key = { i, song -> song.id }) { index, song ->
                    Surface(
                        onClick = { onSongClick(index) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            luzzr.muse.ui.components.AlbumArtThumbnail(
                                artworkUri = song.artworkUri,
                                placeholder = song.title.take(1).uppercase(),
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    song.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    song.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                song.formattedDuration,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
