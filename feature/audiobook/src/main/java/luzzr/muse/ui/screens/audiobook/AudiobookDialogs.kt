package luzzr.muse.ui.screens.audiobook

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import luzzr.muse.domain.model.BookCollection
import luzzr.muse.domain.model.BookCollectionImportFailureStage
import luzzr.muse.domain.model.BookCollectionImportResult
import luzzr.muse.domain.model.BookCollectionItem
import luzzr.muse.domain.model.Song
import luzzr.muse.feature.audiobook.R
import luzzr.muse.ui.theme.AppSpacing

@Composable
fun CreateCollectionDialog(visible: Boolean, onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    if (!visible) return
    var collectionName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.audiobook_new_collection_title)) },
        text = {
            OutlinedTextField(
                value = collectionName,
                onValueChange = { collectionName = it },
                label = { Text(stringResource(R.string.audiobook_collection_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(collectionName.trim()) },
                enabled = collectionName.isNotBlank()
            ) {
                Text(stringResource(R.string.audiobook_action_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.audiobook_action_cancel)) }
        }
    )
}

@Composable
fun AddSongsDialog(
    visible: Boolean,
    collectionId: Long?,
    audiobooks: List<Song>,
    currentItems: List<BookCollectionItem>,
    onDismiss: () -> Unit,
    onAdd: (Long, List<Long>) -> Unit
) {
    if (!visible || collectionId == null) return
    val currentSongIds = currentItems.map { it.song.id }.toSet()
    val availableSongs = audiobooks.filter { it.id !in currentSongIds }
    val selectedSongs = remember(collectionId, availableSongs) { mutableStateListOf<Long>() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.audiobook_select_chapters_title)) },
        text = {
            if (availableSongs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(R.string.audiobook_all_added))
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                    items(availableSongs) { song ->
                        SelectableAudiobookRow(
                            song = song,
                            selected = song.id in selectedSongs,
                            onSelectedChange = { selected ->
                                if (selected) selectedSongs.add(song.id) else selectedSongs.remove(song.id)
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(collectionId, selectedSongs.toList()) },
                enabled = selectedSongs.isNotEmpty()
            ) {
                Text(stringResource(R.string.audiobook_action_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.audiobook_action_cancel)) }
        }
    )
}

@Composable
private fun SelectableAudiobookRow(song: Song, selected: Boolean, onSelectedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelectedChange(!selected) }
            .padding(vertical = AppSpacing.xxs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = selected, onCheckedChange = onSelectedChange)
        Spacer(Modifier.width(AppSpacing.xs))
        Column {
            Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
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

@Composable
fun SortOrderDialog(
    song: Song?,
    collectionId: Long?,
    input: String,
    onInputChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (Long, Long, Int) -> Unit
) {
    if (song == null || collectionId == null) return
    val order = input.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.audiobook_edit_order_title)) },
        text = {
            Column {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = AppSpacing.sm)
                )
                OutlinedTextField(
                    value = input,
                    onValueChange = { value ->
                        if (value.all(Char::isDigit) && value.length <= 2) onInputChange(value)
                    },
                    label = { Text(stringResource(R.string.audiobook_order_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { order?.let { onConfirm(collectionId, song.id, it) } },
                enabled = order in 1..99
            ) {
                Text(stringResource(R.string.audiobook_action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.audiobook_action_cancel)) }
        }
    )
}

@Composable
fun DeleteCollectionDialog(collection: BookCollection?, onDismiss: () -> Unit, onDelete: (Long) -> Unit) {
    if (collection == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.audiobook_delete_collection_title)) },
        text = { Text(stringResource(R.string.audiobook_delete_collection_message, collection.name)) },
        confirmButton = {
            TextButton(onClick = { onDelete(collection.id) }) {
                Text(stringResource(R.string.audiobook_action_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.audiobook_action_cancel)) }
        }
    )
}

@Composable
fun AddToCollectionDialog(song: Song?, collections: List<BookCollection>, onDismiss: () -> Unit, onAdd: (Long, Long) -> Unit) {
    if (song == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.audiobook_add_to_collection_title)) },
        text = {
            if (collections.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(R.string.audiobook_no_collections))
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                    items(collections) { collection ->
                        TextButton(
                            onClick = { onAdd(collection.id, song.id) },
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
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.audiobook_action_cancel)) }
        }
    )
}

@Composable
fun EbookImportPreviewDialog(preview: EbookImportPreview?, isImporting: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    if (preview == null || isImporting) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.audiobook_import_preview_title)) },
        text = {
            Column {
                preview.metadata.coverBytes?.let { cover ->
                    AsyncImage(
                        model = cover,
                        contentDescription = null,
                        modifier = Modifier.size(120.dp).align(Alignment.CenterHorizontally),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.height(AppSpacing.md))
                }
                ImportPreviewRow(stringResource(R.string.audiobook_import_book_title), preview.finalTitle)
                ImportPreviewRow(
                    stringResource(R.string.audiobook_import_author),
                    preview.metadata.author.ifBlank { stringResource(R.string.audiobook_import_keep_existing) }
                )
                ImportPreviewRow(
                    stringResource(R.string.audiobook_import_cover),
                    if (preview.metadata.coverBytes != null) {
                        stringResource(R.string.audiobook_import_cover_found)
                    } else {
                        stringResource(R.string.audiobook_import_keep_existing)
                    }
                )
                ImportPreviewRow(
                    stringResource(R.string.audiobook_import_chapter_count_label),
                    preview.chapterCount.toString()
                )
                preview.exampleTitle?.let {
                    ImportPreviewRow(stringResource(R.string.audiobook_import_title_example), it)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.audiobook_import_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.audiobook_action_cancel)) }
        }
    )
}

@Composable
private fun ImportPreviewRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = AppSpacing.sm)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun EbookImportProgressDialog(isParsing: Boolean, isImporting: Boolean, completed: Int, total: Int) {
    if (!isParsing && !isImporting) return
    AlertDialog(
        onDismissRequest = {},
        title = {
            Text(
                if (isParsing) stringResource(R.string.audiobook_import_parsing) else stringResource(R.string.audiobook_import_running)
            )
        },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(AppSpacing.md))
                Text(
                    if (isParsing) {
                        stringResource(R.string.audiobook_import_reading_file)
                    } else {
                        stringResource(R.string.audiobook_import_progress, completed, total)
                    }
                )
            }
        },
        confirmButton = {}
    )
}

@Composable
fun EbookImportResultDialog(result: BookCollectionImportResult?, error: String?, onDismiss: () -> Unit) {
    if (result == null && error == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (error == null) stringResource(R.string.audiobook_import_complete) else stringResource(R.string.audiobook_import_failed)
            )
        },
        text = {
            if (error != null) {
                Text(error)
            } else if (result != null) {
                Column {
                    Text(stringResource(R.string.audiobook_import_summary, result.successCount, result.totalCount))
                    if (result.failures.isNotEmpty()) {
                        Spacer(Modifier.height(AppSpacing.sm))
                        Text(
                            stringResource(R.string.audiobook_import_failure_count, result.failures.size),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error
                        )
                        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp)) {
                            items(result.failures) { failure ->
                                val stage = when (failure.stage) {
                                    BookCollectionImportFailureStage.METADATA -> stringResource(R.string.audiobook_import_stage_metadata)
                                    BookCollectionImportFailureStage.ARTWORK -> stringResource(R.string.audiobook_import_stage_artwork)
                                }
                                Text(
                                    text = "${failure.originalTitle} · $stage${failure.message?.let { ": $it" }.orEmpty()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(vertical = AppSpacing.xxs)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.audiobook_action_confirm)) }
        }
    )
}
