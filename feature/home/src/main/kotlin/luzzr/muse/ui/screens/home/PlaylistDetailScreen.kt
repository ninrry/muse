package luzzr.muse.ui.screens.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import luzzr.muse.domain.model.Song
import luzzr.muse.feature.home.R
import luzzr.muse.ui.components.AlbumArtThumbnail
import luzzr.muse.ui.components.MuseDualActionRow
import luzzr.muse.ui.components.MusePrimaryAction
import luzzr.muse.ui.components.SongListItem
import luzzr.muse.ui.theme.AppSpacing
import luzzr.muse.ui.theme.MuseDimens
import luzzr.muse.ui.theme.MuseIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlistName: String,
    playlistArtworkUri: String? = null,
    songs: List<Song>,
    isLoading: Boolean,
    currentSong: Song?,
    innerPadding: PaddingValues = PaddingValues(),
    onBack: () -> Unit = {},
    onPlayAll: () -> Unit = {},
    onShuffle: () -> Unit = {},
    onPlaySong: (Int) -> Unit = {},
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
    onUpdateArtwork: (String?) -> Unit = {},
    onRemoveSong: (Long) -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onUpdateArtwork(it.toString()) }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = innerPadding.calculateBottomPadding()),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = playlistName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            MuseIcons.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            MuseIcons.MoreVert,
                            contentDescription = stringResource(R.string.cd_more_options)
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.playlist_edit)) },
                            onClick = {
                                showMenu = false
                                onEdit()
                            },
                            leadingIcon = {
                                Icon(MuseIcons.Edit, contentDescription = null)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.playlist_change_cover)) },
                            onClick = {
                                showMenu = false
                                imagePickerLauncher.launch("image/*")
                            },
                            leadingIcon = {
                                Icon(MuseIcons.Image, contentDescription = null)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.playlist_delete)) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            },
                            leadingIcon = {
                                Icon(
                                    MuseIcons.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { scaffoldPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (songs.isEmpty()) {
                EmptyPlaylistState()
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    PlaylistHeader(
                        playlistName = playlistName,
                        artworkUri = playlistArtworkUri,
                        songCount = songs.size,
                        onPlayAll = onPlayAll,
                        onShuffle = onShuffle
                    )

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = AppSpacing.xs),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        itemsIndexed(
                            items = songs,
                            key = { _, song -> song.id }
                        ) { index, song ->
                            SongListItem(
                                song = song,
                                isPlaying = currentSong?.id == song.id,
                                onClick = { onPlaySong(index) },
                                artworkSize = MuseDimens.ArtworkSizeSmall
                            )
                        }
                        item { Spacer(Modifier.height(MuseDimens.MiniPlayerClearance)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistHeader(playlistName: String, artworkUri: String?, songCount: Int, onPlayAll: () -> Unit, onShuffle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm)
    ) {
        // 歌单封面
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(AppSpacing.md))
        ) {
            AlbumArtThumbnail(
                artworkUri = artworkUri,
                placeholder = playlistName.take(1).uppercase(),
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(Modifier.width(AppSpacing.md))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlistName,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(AppSpacing.xxs))
            Text(
                text = stringResource(R.string.playlist_song_count, songCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(AppSpacing.sm))

            MuseDualActionRow(
                primary = MusePrimaryAction(
                    label = stringResource(R.string.home_play_all),
                    icon = MuseIcons.Play,
                    onClick = onPlayAll
                ),
                secondary = MusePrimaryAction(
                    label = stringResource(R.string.home_shuffle),
                    icon = MuseIcons.Shuffle,
                    onClick = onShuffle
                )
            )
        }
    }
}

@Composable
private fun EmptyPlaylistState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(AppSpacing.xlg)
        ) {
            Icon(
                MuseIcons.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(AppSpacing.xxxlg),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(AppSpacing.md))
            Text(
                stringResource(R.string.playlist_empty),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
