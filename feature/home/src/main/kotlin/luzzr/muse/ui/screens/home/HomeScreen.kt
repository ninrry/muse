package luzzr.muse.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import luzzr.muse.domain.model.GreetingPeriod
import luzzr.muse.domain.model.Playlist
import luzzr.muse.domain.model.Song
import luzzr.muse.feature.home.R
import luzzr.muse.ui.components.AlbumArtThumbnail
import luzzr.muse.ui.components.LocalReduceMotion
import luzzr.muse.ui.components.SongListItem
import luzzr.muse.ui.haptic.pressScale
import luzzr.muse.ui.animation.MotionList
import luzzr.muse.ui.theme.AppSpacing
import luzzr.muse.ui.theme.MuseDimens
import luzzr.muse.ui.theme.MuseShapeTokens

@Composable
fun HomeScreen(
    songs: List<Song>,
    dailyRecommendation: List<Song> = emptyList(),
    playlists: List<Playlist> = emptyList(),
    isScanning: Boolean,
    scanProgress: Int,
    currentSong: Song?,
    greeting: GreetingPeriod,
    innerPadding: PaddingValues = PaddingValues(),
    hasPermission: Boolean = true,
    onRequestPermission: () -> Unit = {},
    onScan: () -> Unit = {},
    onPlaySong: (Song) -> Unit = {},
    onPlaylistClick: (Long) -> Unit = {},
    onCreatePlaylist: () -> Unit = {}
) {
    val reduceMotion = LocalReduceMotion.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (songs.isEmpty() && playlists.isEmpty()) {
            EmptyState(
                isScanning = isScanning,
                scanProgress = scanProgress,
                hasPermission = hasPermission,
                onRequestPermission = onRequestPermission,
                onScan = onScan
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = AppSpacing.sm)
            ) {
                // 歌单区域
                if (playlists.isNotEmpty() || true) {
                    PlaylistSection(
                        playlists = playlists,
                        onPlaylistClick = onPlaylistClick,
                        onCreatePlaylist = onCreatePlaylist
                    )
                }

                val displaySongs = dailyRecommendation.ifEmpty { songs.take(20) }
                if (displaySongs.isNotEmpty()) {
                    SectionLabel(
                        text = stringResource(R.string.home_daily_recommend),
                        modifier = Modifier.padding(horizontal = AppSpacing.md, vertical = AppSpacing.xs)
                    )
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    itemsIndexed(
                        items = displaySongs,
                        key = { _, song -> song.id }
                    ) { _, song ->
                        Box(
                            modifier = if (reduceMotion) {
                                Modifier
                            } else {
                                Modifier.animateItem(
                                    fadeInSpec = MotionList.fadeIn,
                                    fadeOutSpec = MotionList.fadeOut,
                                    placementSpec = MotionList.placement
                                )
                            }
                        ) {
                            SongListItem(
                                song = song,
                                isPlaying = currentSong?.id == song.id,
                                onClick = { onPlaySong(song) },
                                artworkSize = MuseDimens.ArtworkSizeMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall.copy(
            fontWeight = FontWeight.SemiBold
        ),
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
    )
}

@Composable
private fun EmptyState(
    isScanning: Boolean,
    scanProgress: Int,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onScan: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(horizontal = AppSpacing.xlg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(AppSpacing.xxxlg),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)
            )
            Spacer(Modifier.height(AppSpacing.md))
            Text(
                stringResource(R.string.home_empty),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(AppSpacing.xs))
            Text(
                if (hasPermission) stringResource(R.string.home_scan_hint) else stringResource(R.string.home_permission_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(AppSpacing.xlg))

            if (!hasPermission) {
                FilledTonalButton(
                    onClick = onRequestPermission,
                    modifier = Modifier
                        .height(MuseDimens.ButtonHeightLarge)
                        .fillMaxWidth(),
                    shape = MuseShapeTokens.Pill
                ) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(MuseDimens.IconSizeNormal)
                    )
                    Spacer(Modifier.width(AppSpacing.xs))
                    Text(stringResource(R.string.home_grant_permission), style = MaterialTheme.typography.labelLarge)
                }
            } else if (isScanning) {
                LinearProgressIndicator(
                    progress = { scanProgress / 100f },
                    modifier = Modifier.fillMaxWidth().height(MuseDimens.ProgressBarHeight)
                )
                Spacer(Modifier.height(AppSpacing.xs))
                Text(stringResource(R.string.home_scanning), style = MaterialTheme.typography.bodyMedium)
            } else {
                FilledTonalButton(
                    onClick = onScan,
                    modifier = Modifier
                        .height(MuseDimens.ButtonHeightLarge)
                        .fillMaxWidth(),
                    shape = MuseShapeTokens.Pill
                ) {
                    Icon(
                        Icons.Default.Radar,
                        contentDescription = null,
                        modifier = Modifier.size(MuseDimens.IconSizeNormal)
                    )
                    Spacer(Modifier.width(AppSpacing.xs))
                    Text(stringResource(R.string.home_start_scan), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistSection(
    playlists: List<Playlist>,
    onPlaylistClick: (Long) -> Unit,
    onCreatePlaylist: () -> Unit
) {
    Column(modifier = Modifier.padding(vertical = AppSpacing.xs)) {
        SectionLabel(
            text = stringResource(R.string.home_playlists),
            modifier = Modifier.padding(horizontal = AppSpacing.md, vertical = AppSpacing.xs)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = AppSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
        ) {
            item {
                val createInteraction = remember { MutableInteractionSource() }
                val createDescription = stringResource(R.string.home_create_playlist)
                Card(
                    onClick = onCreatePlaylist,
                    modifier = Modifier
                        .width(MuseDimens.PlaylistCardWidth)
                        .height(MuseDimens.PlaylistCardHeight)
                        .semantics {
                            contentDescription = createDescription
                        }
                        .pressScale(createInteraction, 0.96f),
                    shape = MuseShapeTokens.Album,
                    interactionSource = createInteraction,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    ),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp,
                        focusedElevation = 0.dp,
                        hoveredElevation = 0.dp
                    )
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(horizontal = AppSpacing.xs),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    shape = MuseShapeTokens.Pill
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(26.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.height(AppSpacing.xs))
                        Text(
                            stringResource(R.string.home_create_playlist),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            items(playlists, key = { it.id }) { playlist ->
                val cardInteraction = remember(playlist.id) { MutableInteractionSource() }
                val playlistDescription = buildString {
                    append(playlist.name)
                    append(", ")
                    append(stringResource(R.string.playlist_songs, playlist.songCount))
                }
                Card(
                    onClick = { onPlaylistClick(playlist.id) },
                    modifier = Modifier
                        .width(MuseDimens.PlaylistCardWidth)
                        .height(MuseDimens.PlaylistCardHeight)
                        .semantics {
                            contentDescription = playlistDescription
                        }
                        .animateItem()
                        .pressScale(cardInteraction, 0.96f),
                    shape = MuseShapeTokens.Album,
                    interactionSource = cardInteraction,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp,
                        focusedElevation = 0.dp,
                        hoveredElevation = 0.dp
                    )
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (playlist.artworkUri != null) {
                            AlbumArtThumbnail(
                                artworkUri = playlist.artworkUri,
                                placeholder = playlist.name.take(1).uppercase(),
                                modifier = Modifier.fillMaxSize()
                            )
                            // Bottom gradient scrim — keeps cover vivid, text readable
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(64.dp)
                                    .align(Alignment.BottomCenter)
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                MaterialTheme.colorScheme.scrim.copy(alpha = 0.38f)
                                            )
                                        )
                                    )
                            )
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .fillMaxWidth()
                                    .padding(AppSpacing.sm)
                            ) {
                                Text(
                                    playlist.name,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = Color.White
                                )
                                Text(
                                    stringResource(R.string.playlist_songs, playlist.songCount),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.8f),
                                    maxLines = 1
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = AppSpacing.xs, vertical = AppSpacing.sm),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Default.MusicNote,
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                                )
                                Spacer(Modifier.height(AppSpacing.xs))
                                Text(
                                    playlist.name,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    stringResource(R.string.playlist_songs, playlist.songCount),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
