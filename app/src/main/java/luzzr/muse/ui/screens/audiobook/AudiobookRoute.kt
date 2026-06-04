package luzzr.muse.ui.screens.audiobook

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun AudiobookRoute(
    innerPadding: PaddingValues,
    viewModel: AudiobookViewModel = viewModel()
) {
    val audiobooks by viewModel.audiobooks.collectAsStateWithLifecycle()
    val recentAudiobooks by viewModel.recentAudiobooks.collectAsStateWithLifecycle()
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    val selectedCollectionId by viewModel.selectedCollectionId.collectAsStateWithLifecycle()
    val currentCollectionItems by viewModel.currentCollectionItems.collectAsStateWithLifecycle()

    AudiobookScreen(
        innerPadding = innerPadding,
        audiobooks = audiobooks,
        recentAudiobooks = recentAudiobooks,
        collections = collections,
        selectedCollectionId = selectedCollectionId,
        currentCollectionItems = currentCollectionItems,
        onSelectCollection = viewModel::selectCollection,
        onCreateCollection = viewModel::createCollection,
        onDeleteCollection = viewModel::deleteCollection,
        onAddSongsToCollection = viewModel::addSongsToCollection,
        onRemoveSongFromCollection = viewModel::removeSongFromCollection,
        onUpdateSongSortOrder = viewModel::updateSongSortOrder,
        onPlayAudiobook = viewModel::playAudiobook,
        onPlayCollection = viewModel::playCollection,
        getProgressPercent = viewModel::getSavedProgressPercent
    )
}
