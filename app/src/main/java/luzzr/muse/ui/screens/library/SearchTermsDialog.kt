package luzzr.muse.ui.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import luzzr.muse.data.model.Song

/**
 * Dialog that lets the user review and edit search terms before
 * submitting a metadata search. Pre-filled with the song's current
 * title and artist.
 *
 * Used in LibraryScreen before calling [MetadataFetcher.searchExact].
 */
@Composable
fun SearchTermsDialog(
    song: Song,
    onConfirm: (title: String, artist: String) -> Unit,
    onDismiss: () -> Unit
) {
    var searchTitle by remember(song.id) { mutableStateOf(song.title) }
    var searchArtist by remember(song.id) { mutableStateOf(song.artist) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("网络搜索元数据") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = searchTitle,
                    onValueChange = { searchTitle = it },
                    singleLine = true,
                    label = { Text("歌曲名") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("输入歌曲名称…") }
                )
                OutlinedTextField(
                    value = searchArtist,
                    onValueChange = { searchArtist = it },
                    singleLine = true,
                    label = { Text("艺术家") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("输入艺术家名称…") }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(searchTitle, searchArtist) },
                enabled = searchTitle.isNotBlank()
            ) {
                Text("确认搜索")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
