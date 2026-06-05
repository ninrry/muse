package luzzr.muse.ui.screens.library

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import android.view.WindowManager
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogWindowProvider
import luzzr.muse.core.log.MuseLog
import luzzr.muse.domain.model.Song
import luzzr.muse.feature.library.R
import luzzr.muse.ui.theme.AppSpacing
import luzzr.muse.ui.theme.MuseDimens
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

private const val MAX_ARTWORK_BYTES = 10L * 1024L * 1024L
private const val ARTWORK_BUFFER_BYTES = 8 * 1024
private const val TAG = "MetadataEditDialog"

/**
 * Full metadata editor dialog ->lets the user edit title, artist, album,
 * year, genre, and artwork for a single song.
 *
 * Extracted from LibraryScreen for better separation of concerns.
 */
@Composable
fun MetadataEditDialog(
    song: Song,
    error: String?,
    isSaving: Boolean,
    onGenerateDefaultCover: (String) -> ByteArray?,
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
                    artworkError = context.getString(R.string.metadata_cover_too_large)
                }
                ArtworkReadResult.Unavailable -> {
                    selectedArtworkUri = null
                    selectedArtworkBytes = null
                    artworkError = context.getString(R.string.metadata_cover_read_failed)
                }
            }
        }
    }
    val coverDescription = stringResource(R.string.metadata_cover_preview_description)

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text(stringResource(R.string.metadata_edit_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                // Artwork section
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                ) {
                    Surface(
                        modifier = Modifier.size(MuseDimens.ArtworkSizeLarge).clip(RoundedCornerShape(AppSpacing.sm)),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        tonalElevation = AppSpacing.xxxs
                    ) {
                        if (selectedArtworkUri != null) {
                            coil.compose.AsyncImage(
                                model = selectedArtworkUri,
                                contentDescription = coverDescription,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else if (song.artworkUri != null) {
                            coil.compose.AsyncImage(
                                model = song.artworkUri,
                                contentDescription = coverDescription,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Image,
                                    contentDescription = stringResource(R.string.metadata_cover_placeholder_description),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Column {
                        OutlinedButton(
                            onClick = { artworkPicker.launch("image/*") },
                            enabled = !isSaving
                        ) {
                            Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(AppSpacing.md))
                            Spacer(Modifier.width(AppSpacing.xxs))
                            Text(stringResource(R.string.metadata_select_cover), style = MaterialTheme.typography.labelMedium)
                        }
                        Spacer(Modifier.height(AppSpacing.xxs))
                        TextButton(
                            enabled = !isSaving,
                            onClick = {
                                val defaultBytes = onGenerateDefaultCover(song.title)
                                if (defaultBytes == null) {
                                    selectedArtworkBytes = null
                                    selectedArtworkUri = null
                                    artworkError = context.getString(R.string.metadata_cover_generate_failed)
                                    return@TextButton
                                }
                                selectedArtworkBytes = defaultBytes
                                try {
                                    val tempFile = File(
                                        context.cacheDir,
                                        "cover_preview_${song.id}_${System.nanoTime()}.png"
                                    )
                                    tempFile.outputStream().use { it.write(defaultBytes) }
                                    selectedArtworkUri = Uri.fromFile(tempFile)
                                    artworkError = null
                                } catch (e: IOException) {
                                    MuseLog.w(TAG, "Failed to create generated cover preview", e)
                                    selectedArtworkUri = null
                                    artworkError = context.getString(R.string.metadata_cover_preview_failed)
                                } catch (e: SecurityException) {
                                    MuseLog.w(TAG, "No permission to create generated cover preview", e)
                                    selectedArtworkUri = null
                                    artworkError = context.getString(R.string.metadata_cover_preview_failed)
                                }
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
                    label = { Text(stringResource(R.string.metadata_title)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = editArtist,
                    onValueChange = { editArtist = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.metadata_artist)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = editAlbum,
                    onValueChange = { editAlbum = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.metadata_album)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = editYear,
                    onValueChange = { editYear = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.metadata_year)) },
                    placeholder = { Text(stringResource(R.string.metadata_year_example)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = editGenre,
                    onValueChange = { editGenre = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.metadata_genre)) },
                    placeholder = { Text(stringResource(R.string.metadata_genre_example)) },
                    modifier = Modifier.fillMaxWidth()
                )
                artworkError?.let {
                    MetadataErrorText(it)
                }
                error?.let {
                    MetadataErrorText(it)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(editTitle, editArtist, editAlbum, editYear, editGenre, selectedArtworkBytes) },
                enabled = editTitle.isNotBlank() && !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(AppSpacing.md),
                        strokeWidth = AppSpacing.xxxs
                    )
                    Spacer(Modifier.width(AppSpacing.xxs))
                }
                Text(stringResource(R.string.metadata_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text(stringResource(R.string.metadata_cancel))
            }
        }
    )
}

@Composable
private fun MetadataErrorText(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall
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
    } catch (e: IOException) {
        MuseLog.w(TAG, "Failed to read selected artwork", e)
        ArtworkReadResult.Unavailable
    } catch (e: SecurityException) {
        MuseLog.w(TAG, "No permission to read selected artwork", e)
        ArtworkReadResult.Unavailable
    }
}

private sealed interface ArtworkReadResult {
    class Success(val bytes: ByteArray) : ArtworkReadResult
    data object TooLarge : ArtworkReadResult
    data object Unavailable : ArtworkReadResult
}
