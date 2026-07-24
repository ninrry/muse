package luzzr.muse.ui.screens.audiobook

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesomeMotion
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import luzzr.muse.domain.model.ReadAlongBook
import luzzr.muse.domain.model.ReadAlongBookSummary
import luzzr.muse.domain.model.ReadAlongProgress
import luzzr.muse.domain.model.ReadAlongReadingStats
import luzzr.muse.domain.model.ReadAlongShelfFilter
import luzzr.muse.domain.model.ReadAlongSortOrder
import luzzr.muse.domain.model.ReadAlongSyncStatus
import luzzr.muse.feature.audiobook.R
import luzzr.muse.ui.components.DefaultAlbumCover
import luzzr.muse.ui.theme.AppSpacing
import luzzr.muse.ui.theme.MuseShapeTokens
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadAlongShelfRoute(
    innerPadding: PaddingValues,
    onOpenBook: (String) -> Unit,
    viewModel: ReadAlongViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel()
) {
    val sort by viewModel.sortFlow.collectAsStateWithLifecycle()
    val filter by viewModel.filterFlow.collectAsStateWithLifecycle()
    val summaries by viewModel.summaries.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val shelfState by viewModel.shelf.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showImportChooser by remember { mutableStateOf(false) }
    var showSort by remember { mutableStateOf(false) }
    var showFilter by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ReadAlongBookSummary?>(null) }
    var pendingAttach by remember { mutableStateOf<ReadAlongBookSummary?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val (name, mime) = queryDisplay(context, uri)
            viewModel.importPackage(uri, name, mime)
        }
    }
    val treePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) viewModel.importDocumentTree(uri)
    }
    val attachPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        val target = pendingAttach ?: return@rememberLauncherForActivityResult
        if (uris.isNotEmpty()) {
            viewModel.attachSources(
                target.book.id,
                uris.map { uri ->
                    val (name, mime) = queryDisplay(context, uri)
                    luzzr.muse.domain.model.ReadAlongImportSource(uri.toString(), name, mime)
                }
            )
        }
        pendingAttach = null
    }

    LaunchedEffect(shelfState.importedBook) {
        shelfState.importedBook?.let { imported ->
            viewModel.dismissShelfMessage()
            onOpenBook(imported.id)
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            ShelfTopBar(
                stats = stats,
                isImporting = shelfState.isImporting,
                showImportAction = summaries.isNotEmpty(),
                onImport = { showImportChooser = true },
                onStats = { showStats = true }
            )
            ShelfToolbar(
                sort = sort,
                filter = filter,
                onSort = { showSort = true },
                onFilter = { showFilter = true }
            )
            shelfState.error?.let { message ->
                ShelfMessage(message = message, error = true, onDismiss = viewModel::dismissShelfMessage)
            }
            if (shelfState.warnings.isNotEmpty()) {
                ShelfMessage(
                    message = shelfState.warnings.joinToString(" · "),
                    error = false,
                    onDismiss = viewModel::dismissShelfMessage
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = AppSpacing.lg,
                    end = AppSpacing.lg,
                    top = AppSpacing.sm,
                    bottom = AppSpacing.xxlg
                ),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
            ) {
                if (summaries.isEmpty()) {
                    item(key = "import-shelf") {
                        ImportShelfCard(
                            enabled = !shelfState.isImporting,
                            onClick = { showImportChooser = true }
                        )
                    }
                }
                items(summaries, key = { it.book.id }) { summary ->
                    BookShelfTile(
                        summary = summary,
                        onOpen = { onOpenBook(summary.book.id) },
                        onDelete = { pendingDelete = summary },
                        onAttach = { pendingAttach = summary }
                    )
                }
                if (summaries.isEmpty()) {
                    item(key = "empty-guide") {
                        EmptyShelfGuide()
                    }
                }
            }
        }
    }

    if (showImportChooser) {
        ImportChooserSheet(
            onDismiss = { showImportChooser = false },
            onPackage = {
                showImportChooser = false
                filePicker.launch(arrayOf("application/zip", "application/epub+zip", "application/octet-stream"))
            },
            onFolder = {
                showImportChooser = false
                treePicker.launch(null)
            }
        )
    }
    if (showSort) {
        ChoiceDialog(
            title = "书架排序",
            current = sort,
            values = ReadAlongSortOrder.values().toList(),
            label = ::sortLabel,
            onPick = { viewModel.applySort(it); showSort = false },
            onDismiss = { showSort = false }
        )
    }
    if (showFilter) {
        ChoiceDialog(
            title = "书架筛选",
            current = filter,
            values = ReadAlongShelfFilter.values().toList(),
            label = ::filterLabel,
            onPick = { viewModel.applyFilter(it); showFilter = false },
            onDismiss = { showFilter = false }
        )
    }
    if (showStats) StatsDialog(stats = stats, onDismiss = { showStats = false })
    pendingDelete?.let { summary ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.readalong_shelf_delete_title)) },
            text = { Text(stringResource(R.string.readalong_shelf_delete_message, summary.book.title)) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteBook(summary.book.id); pendingDelete = null }) {
                    Text(stringResource(R.string.readalong_shelf_delete_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.readalong_shelf_delete_cancel))
                }
            }
        )
    }
    if (pendingAttach != null) {
        LaunchedEffect(pendingAttach) {
            attachPicker.launch(arrayOf("audio/*", "application/json", "application/zip", "application/octet-stream"))
        }
    }
}

@Composable
private fun ShelfTopBar(
    stats: ReadAlongReadingStats,
    isImporting: Boolean,
    showImportAction: Boolean,
    onImport: () -> Unit,
    onStats: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.readalong_shelf_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "导入工作流书包，边读边听",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        AssistChip(
            onClick = onStats,
            label = { Text(stats.todayReadMs.toReadableDuration()) },
            leadingIcon = { Icon(Icons.Default.Schedule, null, Modifier.size(16.dp)) },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        )
        Spacer(Modifier.width(AppSpacing.xs))
        if (isImporting) {
            CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.dp)
        } else if (showImportAction) {
            IconButton(onClick = onImport) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.readalong_shelf_pick_file))
            }
        }
    }
}

@Composable
private fun ShelfToolbar(
    sort: ReadAlongSortOrder,
    filter: ReadAlongShelfFilter,
    onSort: () -> Unit,
    onFilter: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
    ) {
        AssistChip(
            onClick = onSort,
            label = { Text(sortLabel(sort)) },
            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, null, Modifier.size(16.dp)) }
        )
        AssistChip(
            onClick = onFilter,
            label = { Text(filterLabel(filter)) },
            leadingIcon = { Icon(Icons.Default.Tune, null, Modifier.size(16.dp)) }
        )
    }
}

@Composable
private fun ImportShelfCard(enabled: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.72f),
        shape = MuseShapeTokens.Card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(AppSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(18.dp)
                ) {}
                Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.height(AppSpacing.sm))
            Text("导入有声书", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(AppSpacing.xxs))
            Text(
                "书包或文件夹",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookShelfTile(
    summary: ReadAlongBookSummary,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onAttach: () -> Unit
) {
    val book = summary.book
    var menu by remember { mutableStateOf(false) }
    val progress = summary.progress
    val ratio = shelfProgress(book, progress)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.72f)
            .combinedClickable(onClick = onOpen, onLongClick = onDelete),
        shape = MuseShapeTokens.Card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.TopEnd
            ) {
                Cover(book = book, modifier = Modifier.fillMaxSize())
                IconButton(onClick = { menu = true }) {
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "更多操作", modifier = Modifier.padding(5.dp))
                    }
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("补齐音频/对齐文件") },
                        leadingIcon = { Icon(Icons.Default.AutoAwesomeMotion, null) },
                        onClick = { menu = false; onAttach() }
                    )
                    DropdownMenuItem(
                        text = { Text("从书架删除") },
                        leadingIcon = { Icon(Icons.Default.DeleteOutline, null) },
                        onClick = { menu = false; onDelete() }
                    )
                }
            }
            Column(modifier = Modifier.padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs)) {
                Text(book.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (book.author.isNotBlank()) {
                    Text(book.author, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(5.dp))
                LinearProgressIndicator(
                    progress = { ratio },
                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                    color = statusColor(book.syncStatus),
                    trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                )
                Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusIcon(book.syncStatus)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = progressLabel(book, progress),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun Cover(book: ReadAlongBook, modifier: Modifier = Modifier) {
    var decodeFailed by remember(book.coverPath) { mutableStateOf(false) }
    val coverFile = remember(book.coverPath) { book.coverPath?.let(::File)?.takeIf(File::isFile) }
    Box(modifier = modifier.clip(MuseShapeTokens.Item), contentAlignment = Alignment.Center) {
        if (coverFile != null && !decodeFailed) {
            AsyncImage(
                model = coverFile,
                contentDescription = book.title,
                contentScale = ContentScale.Crop,
                onError = { decodeFailed = true },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            DefaultAlbumCover(placeholder = book.title.take(1).uppercase(), modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun StatusIcon(status: ReadAlongSyncStatus) {
    val icon = when (status) {
        ReadAlongSyncStatus.READY -> Icons.Default.GraphicEq
        ReadAlongSyncStatus.AUDIO_ONLY -> Icons.Default.GraphicEq
        ReadAlongSyncStatus.EPUB_ONLY -> Icons.AutoMirrored.Filled.MenuBook
    }
    Icon(icon, null, Modifier.size(13.dp), tint = statusColor(status))
}

private fun statusColor(status: ReadAlongSyncStatus): Color = when (status) {
    ReadAlongSyncStatus.READY -> Color(0xFF668C76)
    ReadAlongSyncStatus.AUDIO_ONLY -> Color(0xFFB7834A)
    ReadAlongSyncStatus.EPUB_ONLY -> Color(0xFF7A746B)
}

private fun shelfProgress(book: ReadAlongBook, progress: ReadAlongProgress?): Float {
    if (book.chapters.isEmpty()) return 0f
    val chapter = (progress?.chapterIndex ?: 0).coerceIn(0, book.chapters.lastIndex)
    return ((chapter + (progress?.scrollProgress ?: 0f)) / book.chapters.size.toFloat()).coerceIn(0f, 1f)
}

@Composable
private fun EmptyShelfGuide() {
    Column(
        modifier = Modifier.fillMaxWidth().aspectRatio(0.72f).padding(AppSpacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Book, null, Modifier.size(34.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f))
        Spacer(Modifier.height(AppSpacing.sm))
        Text("书架还是空的", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(AppSpacing.xxs))
        Text("点左侧 + 导入工作流输出的 readalong 书包", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ShelfMessage(message: String, error: Boolean, onDismiss: () -> Unit) {
    val background = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer
    val content = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer
    Surface(color = background, modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.lg, vertical = AppSpacing.xs), shape = MuseShapeTokens.Item) {
        Row(modifier = Modifier.padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
            Text(message, color = content, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("关闭", color = content) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportChooserSheet(onDismiss: () -> Unit, onPackage: () -> Unit, onFolder: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = AppSpacing.xxlg)) {
            Text("导入有声书", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = AppSpacing.lg, vertical = AppSpacing.md))
            Text("支持工作流生成的 .readalong.zip，也支持解压后的完整文件夹。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = AppSpacing.lg))
            Spacer(Modifier.height(AppSpacing.sm))
            ListItem(headlineContent = { Text("选择书包文件") }, supportingContent = { Text("EPUB + manifest + alignment + audio") }, leadingContent = { Icon(Icons.Default.FolderOpen, null) }, modifier = Modifier.combinedClickable(onClick = onPackage, onLongClick = {}))
            ListItem(headlineContent = { Text("选择完整文件夹") }, supportingContent = { Text("适合工作流输出目录，自动保留全部资源") }, leadingContent = { Icon(Icons.Default.FolderOpen, null) }, modifier = Modifier.combinedClickable(onClick = onFolder, onLongClick = {}))
        }
    }
}

@Composable
private fun <T> ChoiceDialog(title: String, current: T, values: List<T>, label: (T) -> String, onPick: (T) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                values.forEach { value ->
                    Row(modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { onPick(value) }, onLongClick = {}), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = value == current, onClick = { onPick(value) })
                        Text(label(value), modifier = Modifier.padding(start = AppSpacing.xs))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun StatsDialog(stats: ReadAlongReadingStats, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("阅读统计") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                Text("累计阅读：${stats.totalReadMs.toReadableDuration()}")
                Text("今天：${stats.todayReadMs.toReadableDuration()}")
                Text("本周：${stats.weekReadMs.toReadableDuration()}")
                Text("连续天数：${stats.consecutiveDays}")
                Text("已读字符：${stats.charactersRead}")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("知道了") } }
    )
}

private fun sortLabel(sort: ReadAlongSortOrder): String = when (sort) {
    ReadAlongSortOrder.RECENT -> "最近阅读"
    ReadAlongSortOrder.TITLE -> "标题"
    ReadAlongSortOrder.AUTHOR -> "作者"
    ReadAlongSortOrder.PROGRESS -> "阅读进度"
    ReadAlongSortOrder.HAS_SYNC -> "同步优先"
}

private fun filterLabel(filter: ReadAlongShelfFilter): String = when (filter) {
    ReadAlongShelfFilter.ALL -> "全部"
    ReadAlongShelfFilter.SYNCED -> "有声同步"
    ReadAlongShelfFilter.AUDIO_ONLY -> "仅音频"
    ReadAlongShelfFilter.EPUB_ONLY -> "仅 EPUB"
    ReadAlongShelfFilter.RECENT -> "最近"
}

private fun progressLabel(status: ReadAlongSyncStatus): String = when (status) {
    ReadAlongSyncStatus.READY -> "有声同步"
    ReadAlongSyncStatus.AUDIO_ONLY -> "有声无对齐"
    ReadAlongSyncStatus.EPUB_ONLY -> "仅文本阅读"
}

private fun progressLabel(book: ReadAlongBook, progress: ReadAlongProgress?): String {
    val currentIndex = progress?.chapterIndex ?: book.initialChapterIndex
    val ordinal = book.readingChapterOrdinal(currentIndex) ?: 1
    val chapterLabel = if (book.readingChapterCount == 0) "无章节" else "第 $ordinal/${book.readingChapterCount} 章"
    return "$chapterLabel · ${progressLabel(book.syncStatus)}"
}

private fun queryDisplay(context: Context, uri: Uri): Pair<String?, String?> {
    val name = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()
    return name to runCatching { context.contentResolver.getType(uri) }.getOrNull()
}

private fun Long.toReadableDuration(): String {
    val seconds = this / 1000L
    val hours = seconds / 3600L
    val minutes = (seconds % 3600L) / 60L
    val rest = seconds % 60L
    return if (hours > 0) String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, rest)
    else if (minutes > 0) String.format(Locale.getDefault(), "%d:%02d", minutes, rest)
    else String.format(Locale.getDefault(), "%d 秒", rest)
}
