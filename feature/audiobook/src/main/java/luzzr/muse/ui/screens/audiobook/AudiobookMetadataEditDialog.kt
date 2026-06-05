package luzzr.muse.ui.screens.audiobook

import android.content.Context
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import coil.compose.AsyncImage
import luzzr.muse.domain.model.Song
import luzzr.muse.feature.audiobook.R
import luzzr.muse.ui.theme.AppSpacing
import java.io.ByteArrayOutputStream
import java.io.IOException

private const val MAX_ARTWORK_BYTES = 10L * 1024L * 1024L
private const val ARTWORK_BUFFER_BYTES = 8 * 1024

@Composable
fun AudiobookMetadataEditDialog(
    song: Song,
    error: String?,
    isSaving: Boolean,
    onSave: (title: String, artist: String, album: String, yearStr: String, genre: String, artworkBytes: ByteArray?) -> Unit,
    onDismiss: () -> Unit
) {
    var editTitle by rememberSaveable(song.id) { mutableStateOf(song.title) }
    var editArtist by rememberSaveable(song.id) { mutableStateOf(song.artist) }
    var selectedArtworkBytes by remember(song.id) { mutableStateOf<ByteArray?>(null) }
    var selectedArtworkUri by remember(song.id) { mutableStateOf<Uri?>(null) }
    var artworkError by rememberSaveable(song.id) { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val view = LocalView.current
    @Suppress("DEPRECATION")
    DisposableEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        onDispose {}
    }
    val artworkPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            when (val readResult = readArtworkBytes(context, uri)) {
                is ArtworkReadResult.Success -> {
                    selectedArtworkUri = uri
                    selectedArtworkBytes = readResult.bytes
                    artworkError = null
                }
                ArtworkReadResult.TooLarge -> {
                    selectedArtworkUri = null
                    selectedArtworkBytes = null
                    artworkError = context.getString(R.string.audiobook_metadata_cover_too_large)
                }
                ArtworkReadResult.Unavailable -> {
                    selectedArtworkUri = null
                    selectedArtworkBytes = null
                    artworkError = context.getString(R.string.audiobook_metadata_cover_read_failed)
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text(stringResource(R.string.audiobook_metadata_edit_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                // Cover Art Section
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                ) {
                    Surface(
                        modifier = Modifier.size(80.dp).clip(RoundedCornerShape(AppSpacing.sm)),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        tonalElevation = AppSpacing.xxxs
                    ) {
                        if (selectedArtworkUri != null) {
                            AsyncImage(
                                model = selectedArtworkUri,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else if (song.artworkUri != null) {
                            AsyncImage(
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
                    OutlinedButton(
                        onClick = { artworkPicker.launch("image/*") },
                        enabled = !isSaving
                    ) {
                        Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(AppSpacing.xxs))
                        Text(stringResource(R.string.audiobook_metadata_select_cover), style = MaterialTheme.typography.labelMedium)
                    }
                }

                OutlinedTextField(
                    value = editTitle,
                    onValueChange = { editTitle = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.audiobook_metadata_title)) },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = editArtist,
                    onValueChange = { editArtist = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.audiobook_metadata_artist)) },
                    modifier = Modifier.fillMaxWidth()
                )

                artworkError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(editTitle, editArtist, song.album, song.year?.toString() ?: "", song.genre, selectedArtworkBytes)
                },
                enabled = editTitle.isNotBlank() && !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(AppSpacing.xxs))
                }
                Text(stringResource(R.string.audiobook_metadata_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text(stringResource(R.string.audiobook_action_cancel))
            }
        }
    )
}

private fun readArtworkBytes(context: Context, uri: Uri): ArtworkReadResult {
    return try {
        val knownLength = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        if (knownLength > MAX_ARTWORK_BYTES) {
            return ArtworkReadResult.TooLarge
        }

        val output = ByteArrayOutputStream()
        context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(ARTWORK_BUFFER_BYTES)
            var totalBytes = 0L
            while (true) {
                val bytesRead = input.read(buffer)
                if (bytesRead == -1) break
                totalBytes += bytesRead
                if (totalBytes > MAX_ARTWORK_BYTES) {
                    return ArtworkReadResult.TooLarge
                }
                output.write(buffer, 0, bytesRead)
            }
        } ?: return ArtworkReadResult.Unavailable

        val bytes = output.toByteArray()
        if (bytes.isEmpty()) {
            ArtworkReadResult.Unavailable
        } else {
            ArtworkReadResult.Success(bytes)
        }
    } catch (@Suppress("SwallowedException") e: IOException) {
        ArtworkReadResult.Unavailable
    } catch (@Suppress("SwallowedException") e: SecurityException) {
        ArtworkReadResult.Unavailable
    }
}

private sealed interface ArtworkReadResult {
    class Success(val bytes: ByteArray) : ArtworkReadResult
    data object TooLarge : ArtworkReadResult
    data object Unavailable : ArtworkReadResult
}
