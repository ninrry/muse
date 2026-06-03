package luzzr.muse.ui.screens.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import luzzr.muse.R
import luzzr.muse.data.model.Song
import luzzr.muse.data.tag.DefaultCoverGenerator
import luzzr.muse.ui.theme.AppSpacing

/**
 * Full metadata editor dialog �?lets the user edit title, artist, album,
 * year, genre, and artwork for a single song.
 *
 * Extracted from LibraryScreen for better separation of concerns.
 */
@Composable
fun MetadataEditDialog(
    song: Song,
    error: String?,
    onSave: (title: String, artist: String, album: String, yearStr: String, genre: String, artworkBytes: ByteArray?) -> Unit,
    onDismiss: () -> Unit
) {
    var editTitle by rememberSaveable(song.id) { mutableStateOf(song.title) }
    var editArtist by rememberSaveable(song.id) { mutableStateOf(song.artist) }
    var editAlbum by rememberSaveable(song.id) { mutableStateOf(song.album) }
    var editYear by rememberSaveable(song.id) { mutableStateOf(song.year?.toString() ?: "") }
    var editGenre by rememberSaveable(song.id) { mutableStateOf(song.genre) }
    // ByteArray and Uri are not Parcelable-safe for rememberSaveable
    var selectedArtworkBytes by remember(song.id) { mutableStateOf<ByteArray?>(null) }
    var selectedArtworkUri by remember(song.id) { mutableStateOf<Uri?>(null) }

    val context = LocalContext.current
    val artworkPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
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
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.metadata_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                // Artwork section
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                ) {
                    Surface(
                        modifier = Modifier.size(72.dp).clip(RoundedCornerShape(AppSpacing.sm)),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        tonalElevation = AppSpacing.xxxs
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
                            Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(AppSpacing.md))
                            Spacer(Modifier.width(AppSpacing.xxs))
                            Text(stringResource(R.string.metadata_select_cover), style = MaterialTheme.typography.labelMedium)
                        }
                        Spacer(Modifier.height(AppSpacing.xxs))
                        TextButton(
                            onClick = {
                                val defaultBytes = DefaultCoverGenerator.generate(song.title)
                                selectedArtworkBytes = defaultBytes
                                try {
                                    val cacheDir = context.cacheDir
                                    val oldFile = java.io.File(cacheDir, "cover_preview_${song.id}.png")
                                    oldFile.delete()
                                    val tempFile = java.io.File(cacheDir, "cover_preview_${song.id}_${System.nanoTime()}.png")
                                    tempFile.outputStream().use { it.write(defaultBytes) }
                                    selectedArtworkUri = Uri.fromFile(tempFile)
                                } catch (_: Exception) { }
                            }
                        ) {
                            Text(stringResource(R.string.metadata_gen_cover), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                OutlinedTextField(
                    value = editTitle,
                    onValueChange = { editTitle = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.metadata_title)) }
                )
                OutlinedTextField(
                    value = editArtist,
                    onValueChange = { editArtist = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.metadata_artist)) }
                )
                OutlinedTextField(
                    value = editAlbum,
                    onValueChange = { editAlbum = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.metadata_album)) }
                )
                OutlinedTextField(
                    value = editYear,
                    onValueChange = { editYear = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.metadata_year)) },
                    placeholder = { Text("如 2024") }
                )
                OutlinedTextField(
                    value = editGenre,
                    onValueChange = { editGenre = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.metadata_genre)) },
                    placeholder = { Text("如 Pop") }
                )
                error?.let {
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
                onClick = { onSave(editTitle, editArtist, editAlbum, editYear, editGenre, selectedArtworkBytes) },
                enabled = editTitle.isNotBlank()
            ) { Text(stringResource(R.string.metadata_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.metadata_cancel)) }
        }
    )
}
