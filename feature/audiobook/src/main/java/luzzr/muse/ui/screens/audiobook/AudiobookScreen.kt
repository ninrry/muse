package luzzr.muse.ui.screens.audiobook

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import luzzr.muse.domain.model.BookCollection
import luzzr.muse.domain.model.BookCollectionItem
import luzzr.muse.domain.model.Song
import luzzr.muse.feature.audiobook.R
import luzzr.muse.ui.state.UiText
import luzzr.muse.ui.state.asString
import luzzr.muse.ui.theme.AppSpacing
import luzzr.muse.ui.theme.MuseDimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudiobookScreen(
    innerPadding: PaddingValues,
    audiobooks: List<Song>,
    recentAudiobooks: List<Song>,
    collections: List<BookCollection>,
    selectedCollectionId: Long?,
    currentCollectionItems: List<BookCollectionItem>,
    songToEdit: Song?,
    editError: UiText?,
    isSavingMetadata: Boolean,
    importState: AudiobookImportState,
    onSelectCollection: (Long?) -> Unit,
    onCreateCollection: (String) -> Unit,
    onDeleteCollection: (Long) -> Unit,
    onAddSongsToCollection: (Long, List<Long>) -> Unit,
    onRemoveSongFromCollection: (Long, Long) -> Unit,
    onUpdateSongSortOrder: (Long, Long, Int) -> Unit,
    onPlayAudiobook: (Song) -> Unit,
    onPlayCollection: (List<BookCollectionItem>, Int) -> Unit,
    getProgressPercent: (Song) -> Int,
    onEditMetadata: (Song) -> Unit,
    onSaveEditedMetadata: (String, String, String, String, String, ByteArray?) -> Unit,
    onCancelEditMetadata: () -> Unit,
    onEbookSelected: (String, String?, String?) -> Unit,
    onConfirmEbookImport: () -> Unit,
    onDismissEbookPreview: () -> Unit,
    onDismissEbookImportResult: () -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var showAddSongsDialog by remember { mutableStateOf(false) }
    var showSortOrderDialogForSong by remember { mutableStateOf<Song?>(null) }
    var sortOrderInput by remember { mutableStateOf("") }
    var showDeleteConfirmDialog by remember { mutableStateOf<BookCollection?>(null) }
    var showAddToCollectionDialogForSong by remember { mutableStateOf<Song?>(null) }

    val currentCollection = collections.find { it.id == selectedCollectionId }
    val context = LocalContext.current
    val ebookPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val displayName = runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            }.getOrNull()
            onEbookSelected(uri.toString(), displayName ?: uri.lastPathSegment, context.contentResolver.getType(uri))
        }
    }

    BackHandler(enabled = selectedCollectionId != null) {
        onSelectCollection(null)
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = innerPadding.calculateBottomPadding()),
        topBar = {
            if (selectedCollectionId != null) {
                AudiobookTopBar(
                    collectionName = currentCollection?.name,
                    isCollectionSelected = true,
                    onBack = { onSelectCollection(null) },
                    onAddChapter = { showAddSongsDialog = true },
                    onImportEbook = {
                        ebookPicker.launch(arrayOf("application/epub+zip", "application/zip", "application/octet-stream"))
                    },
                    importEnabled = !importState.isParsing && !importState.isImporting
                )
            }
        }
    ) { padding ->
        if (selectedCollectionId == null) {
            AudiobookShelfContent(
                modifier = Modifier.fillMaxSize().padding(padding),
                audiobooks = audiobooks,
                recentAudiobooks = recentAudiobooks,
                collections = collections,
                getProgressPercent = getProgressPercent,
                onPlayAudiobook = onPlayAudiobook,
                onCreateCollection = { showCreateDialog = true },
                onSelectCollection = onSelectCollection,
                onDeleteCollection = { showDeleteConfirmDialog = it },
                onAddToCollection = { showAddToCollectionDialogForSong = it },
                onEditMetadata = onEditMetadata
            )
        } else {
            CollectionDetailContent(
                modifier = Modifier.fillMaxSize().padding(padding),
                collection = currentCollection ?: BookCollection(name = stringResource(R.string.audiobook_collection_default)),
                items = currentCollectionItems,
                onPlayCollection = onPlayCollection,
                onEditOrder = { item ->
                    showSortOrderDialogForSong = item.song
                    sortOrderInput = item.sortOrder.toString()
                },
                onRemove = { item ->
                    onRemoveSongFromCollection(selectedCollectionId, item.song.id)
                },
                onEditMetadata = onEditMetadata
            )
        }
    }

    songToEdit?.let { song ->
        AudiobookMetadataEditDialog(
            song = song,
            error = editError?.asString(),
            isSaving = isSavingMetadata,
            onSave = onSaveEditedMetadata,
            onDismiss = onCancelEditMetadata
        )
    }

    CreateCollectionDialog(
        visible = showCreateDialog,
        onDismiss = { showCreateDialog = false },
        onCreate = {
            onCreateCollection(it)
            showCreateDialog = false
        }
    )
    AddSongsDialog(
        visible = showAddSongsDialog,
        collectionId = selectedCollectionId,
        audiobooks = audiobooks,
        currentItems = currentCollectionItems,
        onDismiss = { showAddSongsDialog = false },
        onAdd = { collectionId, songIds ->
            onAddSongsToCollection(collectionId, songIds)
            showAddSongsDialog = false
        }
    )
    SortOrderDialog(
        song = showSortOrderDialogForSong,
        collectionId = selectedCollectionId,
        input = sortOrderInput,
        onInputChange = { sortOrderInput = it },
        onDismiss = { showSortOrderDialogForSong = null },
        onConfirm = { collectionId, songId, order ->
            onUpdateSongSortOrder(collectionId, songId, order)
            showSortOrderDialogForSong = null
        }
    )
    DeleteCollectionDialog(
        collection = showDeleteConfirmDialog,
        onDismiss = { showDeleteConfirmDialog = null },
        onDelete = {
            onDeleteCollection(it)
            showDeleteConfirmDialog = null
        }
    )
    AddToCollectionDialog(
        song = showAddToCollectionDialogForSong,
        collections = collections,
        onDismiss = { showAddToCollectionDialogForSong = null },
        onAdd = { collectionId, songId ->
            onAddSongsToCollection(collectionId, listOf(songId))
            showAddToCollectionDialogForSong = null
        }
    )
    EbookImportPreviewDialog(
        preview = importState.preview,
        isImporting = importState.isImporting,
        onConfirm = onConfirmEbookImport,
        onDismiss = onDismissEbookPreview
    )
    EbookImportProgressDialog(
        isParsing = importState.isParsing,
        isImporting = importState.isImporting,
        completed = importState.completedCount,
        total = importState.totalCount
    )
    EbookImportResultDialog(
        result = importState.result,
        error = importState.error?.asString(),
        onDismiss = onDismissEbookImportResult
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AudiobookTopBar(
    collectionName: String?,
    isCollectionSelected: Boolean,
    onBack: () -> Unit,
    onAddChapter: () -> Unit,
    onImportEbook: () -> Unit,
    importEnabled: Boolean
) {
    TopAppBar(
        title = {
            Text(
                text = if (isCollectionSelected) {
                    collectionName ?: stringResource(R.string.audiobook_collections)
                } else {
                    stringResource(R.string.audiobook_shelf_title)
                },
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
        },
        navigationIcon = {
            if (isCollectionSelected) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.audiobook_action_back)
                    )
                }
            }
        },
        actions = {
            if (isCollectionSelected) {
                IconButton(onClick = onImportEbook, enabled = importEnabled) {
                    Icon(
                        Icons.Default.UploadFile,
                        contentDescription = stringResource(R.string.audiobook_import_ebook),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onAddChapter) {
                    Icon(
                        Icons.Default.AddCircle,
                        contentDescription = stringResource(R.string.audiobook_add_chapter),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}

@Composable
private fun AudiobookShelfContent(
    modifier: Modifier,
    audiobooks: List<Song>,
    recentAudiobooks: List<Song>,
    collections: List<BookCollection>,
    getProgressPercent: (Song) -> Int,
    onPlayAudiobook: (Song) -> Unit,
    onCreateCollection: () -> Unit,
    onSelectCollection: (Long) -> Unit,
    onDeleteCollection: (BookCollection) -> Unit,
    onAddToCollection: (Song) -> Unit,
    onEditMetadata: (Song) -> Unit
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = AppSpacing.lg, vertical = AppSpacing.xs)
    ) {
        if (recentAudiobooks.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.audiobook_recent),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = AppSpacing.sm, top = AppSpacing.sm)
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(recentAudiobooks, key = { it.id }) { song ->
                        RecentAudiobookCard(
                            song = song,
                            progressPercent = getProgressPercent(song),
                            onClick = { onPlayAudiobook(song) }
                        )
                    }
                }
            }
        }
        item {
            Text(
                text = stringResource(R.string.audiobook_collections),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = AppSpacing.lg, bottom = AppSpacing.sm)
            )
        }
        item {
            CollectionGrid(
                collections = collections,
                onCreate = onCreateCollection,
                onSelect = onSelectCollection,
                onDelete = onDeleteCollection
            )
        }
        item {
            Text(
                text = stringResource(R.string.audiobook_all_audio),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = AppSpacing.lg, bottom = AppSpacing.sm)
            )
        }
        if (audiobooks.isEmpty()) {
            item { AudiobookEmptyState() }
        } else {
            items(audiobooks, key = { it.id }) { song ->
                AudiobookFileItem(
                    song = song,
                    onPlay = { onPlayAudiobook(song) },
                    onAddToCollection = { onAddToCollection(song) },
                    onEditMetadata = { onEditMetadata(song) }
                )
            }
        }
    }
}

@Composable
private fun CollectionGrid(
    collections: List<BookCollection>,
    onCreate: () -> Unit,
    onSelect: (Long) -> Unit,
    onDelete: (BookCollection) -> Unit
) {
    Column {
        val rowCount = (collections.size + 2) / 2
        for (row in 0 until rowCount) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = AppSpacing.xxs),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)
            ) {
                CollectionGridCell(
                    collection = collections.getOrNull(row * 2 - 1),
                    isCreateCell = row == 0,
                    modifier = Modifier.weight(1f).height(MuseDimens.CollectionCardHeight),
                    onCreate = onCreate,
                    onSelect = onSelect,
                    onDelete = onDelete
                )
                CollectionGridCell(
                    collection = collections.getOrNull(row * 2),
                    isCreateCell = false,
                    modifier = Modifier.weight(1f).height(MuseDimens.CollectionCardHeight),
                    onCreate = onCreate,
                    onSelect = onSelect,
                    onDelete = onDelete
                )
            }
        }
    }
}

@Composable
private fun CollectionGridCell(
    collection: BookCollection?,
    isCreateCell: Boolean,
    modifier: Modifier,
    onCreate: () -> Unit,
    onSelect: (Long) -> Unit,
    onDelete: (BookCollection) -> Unit
) {
    when {
        isCreateCell -> CreateCollectionCard(modifier = modifier, onClick = onCreate)
        collection != null -> CollectionCard(
            collection = collection,
            modifier = modifier,
            onClick = { onSelect(collection.id) },
            onLongClick = { onDelete(collection) }
        )
        else -> Spacer(modifier)
    }
}

@Composable
private fun AudiobookEmptyState() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = AppSpacing.xlg),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.audiobook_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun CollectionDetailContent(
    modifier: Modifier,
    collection: BookCollection,
    items: List<BookCollectionItem>,
    onPlayCollection: (List<BookCollectionItem>, Int) -> Unit,
    onEditOrder: (BookCollectionItem) -> Unit,
    onRemove: (BookCollectionItem) -> Unit,
    onEditMetadata: (Song) -> Unit
) {
    Column(modifier = modifier) {
        CollectionBannerHeader(
            collection = collection,
            itemCount = items.size,
            onPlayAll = { onPlayCollection(items, 0) }
        )
        Spacer(Modifier.height(AppSpacing.sm))
        if (items.isEmpty()) {
            CollectionEmptyState(Modifier.weight(1f))
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = AppSpacing.lg, vertical = AppSpacing.xs)
            ) {
                itemsIndexed(items, key = { _, item -> item.song.id }) { index, item ->
                    CollectionChapterItem(
                        item = item,
                        onClick = { onPlayCollection(items, index) },
                        onEditOrder = { onEditOrder(item) },
                        onRemove = { onRemove(item) },
                        onEditMetadata = { onEditMetadata(item.song) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CollectionEmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.AutoMirrored.Filled.LibraryBooks,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            )
            Spacer(Modifier.height(AppSpacing.sm))
            Text(
                text = stringResource(R.string.audiobook_collection_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}
