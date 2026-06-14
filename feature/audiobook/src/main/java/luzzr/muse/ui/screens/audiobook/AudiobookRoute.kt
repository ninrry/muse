package luzzr.muse.ui.screens.audiobook

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun AudiobookRoute(innerPadding: PaddingValues, viewModel: AudiobookViewModel = hiltViewModel()) {
    val audiobooks by viewModel.audiobooks.collectAsStateWithLifecycle()
    val recentAudiobooks by viewModel.recentAudiobooks.collectAsStateWithLifecycle()
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    val selectedCollectionId by viewModel.selectedCollectionId.collectAsStateWithLifecycle()
    val currentCollectionItems by viewModel.currentCollectionItems.collectAsStateWithLifecycle()

    val songToEdit by viewModel.songToEdit.collectAsStateWithLifecycle()
    val editError by viewModel.editError.collectAsStateWithLifecycle()
    val isSavingMetadata by viewModel.isSavingMetadata.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()

    AudiobookScreen(
        innerPadding = innerPadding,
        audiobooks = audiobooks,
        recentAudiobooks = recentAudiobooks,
        collections = collections,
        selectedCollectionId = selectedCollectionId,
        currentCollectionItems = currentCollectionItems,
        songToEdit = songToEdit,
        editError = editError,
        isSavingMetadata = isSavingMetadata,
        importState = importState,
        onSelectCollection = viewModel::selectCollection,
        onCreateCollection = viewModel::createCollection,
        onDeleteCollection = viewModel::deleteCollection,
        onAddSongsToCollection = viewModel::addSongsToCollection,
        onRemoveSongFromCollection = viewModel::removeSongFromCollection,
        onUpdateSongSortOrder = viewModel::updateSongSortOrder,
        onPlayAudiobook = viewModel::playAudiobook,
        onPlayCollection = viewModel::playCollection,
        getProgressPercent = viewModel::getSavedProgressPercent,
        onEditMetadata = viewModel::requestEditMetadata,
        onSaveEditedMetadata = viewModel::saveEditedMetadata,
        onCancelEditMetadata = viewModel::cancelEditMetadata,
        onEbookSelected = viewModel::requestEbookPreview,
        onConfirmEbookImport = viewModel::confirmEbookImport,
        onDismissEbookPreview = viewModel::dismissEbookPreview,
        onDismissEbookImportResult = viewModel::dismissEbookImportResult
    )
}
