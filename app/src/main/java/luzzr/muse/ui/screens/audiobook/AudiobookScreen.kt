package luzzr.muse.ui.screens.audiobook

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import luzzr.muse.data.model.Song
import luzzr.muse.domain.model.BookCollection
import luzzr.muse.domain.model.BookCollectionItem
import luzzr.muse.ui.components.DefaultAlbumCover
import luzzr.muse.ui.theme.AppSpacing
import luzzr.muse.ui.theme.MuseDimens

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AudiobookScreen(
    innerPadding: PaddingValues,
    audiobooks: List<Song>,
    recentAudiobooks: List<Song>,
    collections: List<BookCollection>,
    selectedCollectionId: Long?,
    currentCollectionItems: List<BookCollectionItem>,
    onSelectCollection: (Long?) -> Unit,
    onCreateCollection: (String) -> Unit,
    onDeleteCollection: (Long) -> Unit,
    onAddSongsToCollection: (Long, List<Long>) -> Unit,
    onRemoveSongFromCollection: (Long, Long) -> Unit,
    onUpdateSongSortOrder: (Long, Long, Int) -> Unit,
    onPlayAudiobook: (Song) -> Unit,
    onPlayCollection: (List<BookCollectionItem>, Int) -> Unit,
    getProgressPercent: (Song) -> Int
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var showAddSongsDialog by remember { mutableStateOf(false) }
    var showSortOrderDialogForSong by remember { mutableStateOf<Song?>(null) }
    var sortOrderInput by remember { mutableStateOf("") }
    var showDeleteConfirmDialog by remember { mutableStateOf<BookCollection?>(null) }
    var showAddToCollectionDialogForSong by remember { mutableStateOf<Song?>(null) }

    val currentCollection = collections.find { it.id == selectedCollectionId }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = innerPadding.calculateBottomPadding()),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (selectedCollectionId != null) currentCollection?.name ?: "书籍合集" else "有声书书架",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    if (selectedCollectionId != null) {
                        IconButton(onClick = { onSelectCollection(null) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
                actions = {
                    if (selectedCollectionId != null) {
                        IconButton(onClick = { showAddSongsDialog = true }) {
                            Icon(Icons.Default.AddCircle, contentDescription = "添加章节", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (selectedCollectionId == null) {
                // 有声书主界面 (最近播放 + 书籍合集网格 + 未归档列表)
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = AppSpacing.lg, vertical = AppSpacing.xs)
                ) {
                    // 1. 最近播放
                    if (recentAudiobooks.isNotEmpty()) {
                        item {
                            Text(
                                text = "最近收听",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = AppSpacing.sm, top = AppSpacing.sm)
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(recentAudiobooks) { song ->
                                    val percent = getProgressPercent(song)
                                    RecentAudiobookCard(
                                        song = song,
                                        progressPercent = percent,
                                        onClick = { onPlayAudiobook(song) }
                                    )
                                }
                            }
                        }
                    }

                    // 2. 书籍合集网格标题
                    item {
                        Text(
                            text = "书籍合集",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = AppSpacing.lg, bottom = AppSpacing.sm)
                        )
                    }

                    // 3. 书籍合集网格 (用两列的 Card 堆砌)
                    item {
                        Column {
                            val itemsList = collections
                            val rowCount = (itemsList.size + 2) / 2 // +1 for "Create" card
                            for (r in 0 until rowCount) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = AppSpacing.xxs),
                                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)
                                ) {
                                    // Left card
                                    val leftIdx = r * 2 - 1 // idx 0 is Create Card
                                    if (leftIdx == -1) {
                                        // Create card
                                        CreateCollectionCard(
                                            modifier = Modifier.weight(1f).height(110.dp),
                                            onClick = { showCreateDialog = true }
                                        )
                                    } else if (leftIdx < itemsList.size) {
                                        val collection = itemsList[leftIdx]
                                        CollectionCard(
                                            collection = collection,
                                            modifier = Modifier.weight(1f).height(110.dp),
                                            onClick = { onSelectCollection(collection.id) },
                                            onLongClick = { showDeleteConfirmDialog = collection }
                                        )
                                    } else {
                                        Spacer(Modifier.weight(1f))
                                    }

                                    // Right card
                                    val rightIdx = r * 2
                                    if (rightIdx < itemsList.size) {
                                        val collection = itemsList[rightIdx]
                                        CollectionCard(
                                            collection = collection,
                                            modifier = Modifier.weight(1f).height(110.dp),
                                            onClick = { onSelectCollection(collection.id) },
                                            onLongClick = { showDeleteConfirmDialog = collection }
                                        )
                                    } else {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }

                    // 4. 所有未归档有声书音频列表
                    item {
                        Text(
                            text = "所有有声书音频",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = AppSpacing.lg, bottom = AppSpacing.sm)
                        )
                    }

                    if (audiobooks.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = AppSpacing.xlg),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "曲库中无 .ogg 格式的有声书音频\n请在设置中扫描您的有声书文件夹",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        items(audiobooks) { song ->
                            AudiobookFileItem(
                                song = song,
                                onPlay = { onPlayAudiobook(song) },
                                onAddToCollection = { showAddToCollectionDialogForSong = song }
                            )
                        }
                    }
                }
            } else {
                // 有声书合集章节详情界面
                Column(modifier = Modifier.fillMaxSize()) {
                    // 精美合集大 Header Banner
                    CollectionBannerHeader(
                        collectionName = currentCollection?.name ?: "合集",
                        itemCount = currentCollectionItems.size,
                        onPlayAll = { onPlayCollection(currentCollectionItems, 0) }
                    )

                    Spacer(Modifier.height(AppSpacing.sm))

                    if (currentCollectionItems.isEmpty()) {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.LibraryBooks,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                )
                                Spacer(Modifier.height(AppSpacing.sm))
                                Text(
                                    text = "合集中暂无章节，请点击右上角添加章节",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = AppSpacing.lg, vertical = AppSpacing.xs)
                        ) {
                            itemsIndexed(currentCollectionItems) { index, item ->
                                CollectionChapterItem(
                                    item = item,
                                    onClick = { onPlayCollection(currentCollectionItems, index) },
                                    onEditOrder = {
                                        showSortOrderDialogForSong = item.song
                                        sortOrderInput = item.sortOrder.toString()
                                    },
                                    onRemove = {
                                        onRemoveSongFromCollection(selectedCollectionId, item.song.id)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // --- 各类对话框弹出 --------------------------------------------

    // 1. 新建合集对话框
    if (showCreateDialog) {
        var collectionName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("新建书籍合集") },
            text = {
                OutlinedTextField(
                    value = collectionName,
                    onValueChange = { collectionName = it },
                    label = { Text("合集名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (collectionName.isNotBlank()) {
                            onCreateCollection(collectionName.trim())
                            showCreateDialog = false
                        }
                    }
                ) {
                    Text("创建")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 2. 多选添加章节对话框
    if (showAddSongsDialog && selectedCollectionId != null) {
        val currentSongIds = currentCollectionItems.map { it.song.id }.toSet()
        val availableSongs = audiobooks.filter { it.id !in currentSongIds }
        val selectedSongs = remember { mutableStateListOf<Long>() }

        AlertDialog(
            onDismissRequest = { showAddSongsDialog = false },
            title = { Text("选择章节加入合集") },
            text = {
                if (availableSongs.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("所有有声书音频都已加入该合集")
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                        items(availableSongs) { song ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (song.id in selectedSongs) selectedSongs.remove(song.id)
                                        else selectedSongs.add(song.id)
                                    }
                                    .padding(vertical = AppSpacing.xxs),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = song.id in selectedSongs,
                                    onCheckedChange = { checked ->
                                        if (checked == true) selectedSongs.add(song.id)
                                        else selectedSongs.remove(song.id)
                                    }
                                )
                                Spacer(Modifier.width(AppSpacing.xs))
                                Column {
                                    Text(
                                        song.title,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        song.artist,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (selectedSongs.isNotEmpty()) {
                            onAddSongsToCollection(selectedCollectionId, selectedSongs.toList())
                            showAddSongsDialog = false
                        }
                    },
                    enabled = selectedSongs.isNotEmpty()
                ) {
                    Text("添加")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddSongsDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 3. 数字标记排序修改弹窗 (01-99)
    val editingSong = showSortOrderDialogForSong
    if (editingSong != null && selectedCollectionId != null) {
        AlertDialog(
            onDismissRequest = { showSortOrderDialogForSong = null },
            title = { Text("修改章节排序序号") },
            text = {
                Column {
                    Text(
                        text = editingSong.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = AppSpacing.sm)
                    )
                    OutlinedTextField(
                        value = sortOrderInput,
                        onValueChange = { input ->
                            // 仅允许输入 1-2 位数字
                            if (input.all { it.isDigit() } && input.length <= 2) {
                                sortOrderInput = input
                            }
                        },
                        label = { Text("排序号 (01-99)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val num = sortOrderInput.toIntOrNull()
                        if (num != null && num in 1..99) {
                            onUpdateSongSortOrder(selectedCollectionId, editingSong.id, num)
                            showSortOrderDialogForSong = null
                        }
                    },
                    enabled = sortOrderInput.toIntOrNull() != null && sortOrderInput.toInt() in 1..99
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSortOrderDialogForSong = null }) {
                    Text("取消")
                }
            }
        )
    }

    // 4. 删除合集二次确认弹窗
    val deletingCollection = showDeleteConfirmDialog
    if (deletingCollection != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = null },
            title = { Text("删除合集") },
            text = { Text("确认要删除合集 \"${deletingCollection.name}\" 吗？合集内的文件进度不会受影响。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteCollection(deletingCollection.id)
                        showDeleteConfirmDialog = null
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = null }) {
                    Text("取消")
                }
            }
        )
    }

    // 5. 快速添加到合集弹窗
    val addingSong = showAddToCollectionDialogForSong
    if (addingSong != null) {
        AlertDialog(
            onDismissRequest = { showAddToCollectionDialogForSong = null },
            title = { Text("加入书籍合集") },
            text = {
                if (collections.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("暂无合集，请先创建合集")
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                        items(collections) { collection ->
                            TextButton(
                                onClick = {
                                    onAddSongsToCollection(collection.id, listOf(addingSong.id))
                                    showAddToCollectionDialogForSong = null
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    collection.name,
                                    textAlign = TextAlign.Start,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAddToCollectionDialogForSong = null }) {
                    Text("取消")
                }
            }
        )
    }
}

// --- 子微组件 --------------------------------------------------

@Composable
fun RecentAudiobookCard(
    song: Song,
    progressPercent: Int,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .width(150.dp)
            .padding(vertical = AppSpacing.xxs),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(AppSpacing.md)
    ) {
        Column(modifier = Modifier.padding(AppSpacing.sm)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(AppSpacing.sm))
            ) {
                DefaultAlbumCover(
                    placeholder = song.title.take(1).uppercase(),
                    modifier = Modifier.fillMaxSize()
                )
                if (song.artworkUri != null) {
                    AsyncImage(
                        model = song.artworkUri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
            Spacer(Modifier.height(AppSpacing.xs))
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(AppSpacing.xxs))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LinearProgressIndicator(
                    progress = { progressPercent / 100f },
                    modifier = Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                )
                Spacer(Modifier.width(AppSpacing.xs))
                Text(
                    text = "${progressPercent}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun CreateCollectionCard(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(AppSpacing.md),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.height(AppSpacing.xxs))
            Text(
                text = "新建合集",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CollectionCard(
    collection: BookCollection,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick
        ),
        shape = RoundedCornerShape(AppSpacing.md),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        )
                    )
                )
                .padding(AppSpacing.md)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Icon(
                    Icons.Default.LibraryMusic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text(
                        text = collection.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "章节合集",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
fun AudiobookFileItem(
    song: Song,
    onPlay: () -> Unit,
    onAddToCollection: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlay() }
            .padding(vertical = AppSpacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(AppSpacing.sm))
        ) {
            DefaultAlbumCover(
                placeholder = song.title.take(1).uppercase(),
                modifier = Modifier.fillMaxSize()
            )
            if (song.artworkUri != null) {
                AsyncImage(
                    model = song.artworkUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }

        Spacer(Modifier.width(AppSpacing.md))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${song.artist} • ${song.formattedDuration}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(onClick = onAddToCollection) {
            Icon(
                Icons.Default.PlaylistAdd,
                contentDescription = "加入合集",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun CollectionBannerHeader(
    collectionName: String,
    itemCount: Int,
    onPlayAll: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.sm),
        shape = RoundedCornerShape(AppSpacing.md),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = collectionName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(AppSpacing.xxs))
                Text(
                    text = "共 $itemCount 个章节音频",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.width(AppSpacing.md))

            Button(
                onClick = onPlayAll,
                shape = RoundedCornerShape(AppSpacing.sm),
                contentPadding = PaddingValues(horizontal = AppSpacing.md, vertical = AppSpacing.xs)
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(AppSpacing.xxs))
                Text("播放合集")
            }
        }
    }
}

@Composable
fun CollectionChapterItem(
    item: BookCollectionItem,
    onClick: () -> Unit,
    onEditOrder: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = AppSpacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 数字 Badge 按钮 (01-99)，点击弹窗编辑
        Surface(
            onClick = onEditOrder,
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            modifier = Modifier.size(36.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "%02d".format(item.sortOrder),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.width(AppSpacing.md))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.song.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${item.song.artist} • ${item.song.formattedDuration}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(onClick = onRemove) {
            Icon(
                Icons.Default.DeleteOutline,
                contentDescription = "从合集移除",
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
            )
        }
    }
}
