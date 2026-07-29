package luzzr.muse.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import luzzr.muse.domain.model.GreetingPeriod
import luzzr.muse.domain.model.Playlist
import luzzr.muse.domain.model.Song
import luzzr.muse.feature.home.R
import luzzr.muse.ui.animation.MotionList
import luzzr.muse.ui.artwork.rememberArtworkPalette
import luzzr.muse.ui.components.AlbumArtThumbnail
import luzzr.muse.ui.components.LocalReduceMotion
import luzzr.muse.ui.components.MuseEditorialCard
import luzzr.muse.ui.components.MusePage
import luzzr.muse.ui.components.MuseSectionHeader
import luzzr.muse.ui.components.MuseStatusPill
import luzzr.muse.ui.components.SongListItem
import luzzr.muse.ui.haptic.pressScale
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
    MusePage(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
    ) {
        if (songs.isEmpty() && playlists.isEmpty()) {
            EmptyState(
                isScanning = isScanning,
                scanProgress = scanProgress,
                hasPermission = hasPermission,
                onRequestPermission = onRequestPermission,
                onScan = onScan
            )
            return@MusePage
        }

        val displaySongs = dailyRecommendation.ifEmpty { songs.take(20) }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = AppSpacing.xxlg),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            item(key = "home_header") {
                HomeHeader(
                    greeting = greeting,
                    songCount = songs.size,
                    modifier = Modifier.padding(
                        start = AppSpacing.lg,
                        end = AppSpacing.lg,
                        top = AppSpacing.lg,
                        bottom = AppSpacing.md
                    )
                )
            }

            displaySongs.firstOrNull()?.let { featured ->
                item(key = "home_featured_${featured.id}") {
                    FeaturedListeningCard(
                        song = featured,
                        onPlay = { onPlaySong(featured) },
                        modifier = Modifier.padding(horizontal = AppSpacing.lg)
                    )
                    Spacer(Modifier.height(AppSpacing.lg))
                }
            }

            item(key = "home_playlists") {
                PlaylistSection(
                    playlists = playlists,
                    onPlaylistClick = onPlaylistClick,
                    onCreatePlaylist = onCreatePlaylist
                )
            }

            if (displaySongs.isNotEmpty()) {
                item(key = "home_recent_header") {
                    MuseSectionHeader(
                        title = stringResource(R.string.home_daily_recommend),
                        eyebrow = "MUSE SELECTS",
                        supportingText = "${displaySongs.size} 首为此刻准备的本地音乐",
                        modifier = Modifier.padding(
                            start = AppSpacing.lg,
                            end = AppSpacing.lg,
                            top = AppSpacing.lg,
                            bottom = AppSpacing.sm
                        )
                    )
                }
            }

            itemsIndexed(
                items = displaySongs,
                key = { _, song -> song.id },
                contentType = { _, _ -> "song" }
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

@Composable
private fun HomeHeader(greeting: GreetingPeriod, songCount: Int, modifier: Modifier = Modifier) {
    val greetingText = stringResource(
        when (greeting) {
            GreetingPeriod.MORNING -> R.string.greeting_morning
            GreetingPeriod.AFTERNOON -> R.string.greeting_afternoon
            GreetingPeriod.EVENING -> R.string.greeting_evening
        }
    )
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = greetingText,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.home_brand),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "让今天从一张好唱片开始",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        MuseStatusPill(text = "$songCount 首")
    }
}

@Composable
private fun FeaturedListeningCard(
    song: Song,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = rememberArtworkPalette(song.artworkUri)
    val interaction = remember(song.id) { MutableInteractionSource() }
    Surface(
        onClick = onPlay,
        modifier = modifier
            .fillMaxWidth()
            .height(190.dp)
            .pressScale(interaction, 0.985f),
        interactionSource = interaction,
        shape = MuseShapeTokens.Hero,
        color = Color.Transparent,
        shadowElevation = 10.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            palette.ambientStart,
                            palette.ambientMiddle,
                            palette.ambientEnd
                        )
                    )
                )
                .padding(AppSpacing.lg)
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(end = 126.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "TODAY'S RECORD",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        ),
                        color = palette.onPrimary.copy(alpha = 0.74f)
                    )
                    Spacer(Modifier.height(AppSpacing.xs))
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = palette.onPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodyLarge,
                        color = palette.onPrimary.copy(alpha = 0.74f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = MuseShapeTokens.Pill,
                        color = palette.primary
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = AppSpacing.md, vertical = AppSpacing.xs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = stringResource(R.string.home_play_all),
                                tint = palette.onPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(AppSpacing.xxs))
                            Text(
                                stringResource(R.string.home_play_all),
                                style = MaterialTheme.typography.labelLarge,
                                color = palette.onPrimary
                            )
                        }
                    }
                }
            }
            AlbumArtThumbnail(
                artworkUri = song.artworkUri,
                placeholder = song.title.take(1).uppercase(),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(118.dp)
            )
        }
    }
}

@Composable
private fun EmptyState(
    isScanning: Boolean,
    scanProgress: Int,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onScan: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        MuseEditorialCard(
            modifier = Modifier
                .padding(horizontal = AppSpacing.lg)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(AppSpacing.xlg),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier.size(72.dp),
                    shape = MuseShapeTokens.Hero,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.LibraryMusic,
                            contentDescription = null,
                            modifier = Modifier.size(34.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(Modifier.height(AppSpacing.lg))
                Text(
                    stringResource(R.string.home_empty),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(AppSpacing.xs))
                Text(
                    if (hasPermission) {
                        stringResource(R.string.home_scan_hint)
                    } else {
                        stringResource(R.string.home_permission_hint)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(AppSpacing.xlg))
                when {
                    !hasPermission -> Button(
                        onClick = onRequestPermission,
                        modifier = Modifier.fillMaxWidth().height(MuseDimens.ButtonHeightLarge),
                        shape = MuseShapeTokens.Pill
                    ) {
                        Text(stringResource(R.string.home_grant_permission))
                    }
                    isScanning -> {
                        LinearProgressIndicator(
                            progress = { scanProgress / 100f },
                            modifier = Modifier.fillMaxWidth().height(MuseDimens.ProgressBarHeight)
                        )
                        Spacer(Modifier.height(AppSpacing.xs))
                        Text(stringResource(R.string.home_scanning))
                    }
                    else -> FilledTonalButton(
                        onClick = onScan,
                        modifier = Modifier.fillMaxWidth().height(MuseDimens.ButtonHeightLarge),
                        shape = MuseShapeTokens.Pill
                    ) {
                        Icon(Icons.Default.Radar, contentDescription = null)
                        Spacer(Modifier.width(AppSpacing.xs))
                        Text(stringResource(R.string.home_start_scan))
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistSection(
    playlists: List<Playlist>,
    onPlaylistClick: (Long) -> Unit,
    onCreatePlaylist: () -> Unit
) {
    Column {
        MuseSectionHeader(
            title = stringResource(R.string.home_playlists),
            eyebrow = "YOUR COLLECTION",
            supportingText = if (playlists.isEmpty()) "把喜欢的歌曲整理成自己的唱片架" else "${playlists.size} 张私人选集",
            modifier = Modifier.padding(horizontal = AppSpacing.lg)
        )
        Spacer(Modifier.height(AppSpacing.sm))
        LazyRow(
            contentPadding = PaddingValues(horizontal = AppSpacing.lg),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
        ) {
            item(key = "create_playlist") {
                PlaylistCreateCard(onClick = onCreatePlaylist)
            }
            items(playlists, key = { it.id }) { playlist ->
                PlaylistCard(
                    playlist = playlist,
                    onClick = { onPlaylistClick(playlist.id) }
                )
            }
        }
    }
}

@Composable
private fun PlaylistCreateCard(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val description = stringResource(R.string.home_create_playlist)
    Card(
        onClick = onClick,
        modifier = Modifier
            .width(142.dp)
            .height(164.dp)
            .semantics { contentDescription = description }
            .pressScale(interaction, 0.96f),
        interactionSource = interaction,
        shape = MuseShapeTokens.SectionCard,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(AppSpacing.md),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = MuseShapeTokens.Pill,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Column {
                Text(
                    stringResource(R.string.home_create_playlist),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    "新建选集",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.68f)
                )
            }
        }
    }
}

@Composable
private fun PlaylistCard(playlist: Playlist, onClick: () -> Unit) {
    val interaction = remember(playlist.id) { MutableInteractionSource() }
    val description = "${playlist.name}, ${stringResource(R.string.playlist_songs, playlist.songCount)}"
    Card(
        onClick = onClick,
        modifier = Modifier
            .width(142.dp)
            .height(164.dp)
            .semantics { contentDescription = description }
            .pressScale(interaction, 0.96f),
        interactionSource = interaction,
        shape = MuseShapeTokens.SectionCard,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (playlist.artworkUri != null) {
                AlbumArtThumbnail(
                    artworkUri = playlist.artworkUri,
                    placeholder = playlist.name.take(1).uppercase(),
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, MaterialTheme.colorScheme.scrim.copy(alpha = 0.74f))
                            )
                        )
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(AppSpacing.md)
            ) {
                if (playlist.artworkUri == null) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(AppSpacing.sm))
                }
                Text(
                    playlist.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (playlist.artworkUri != null) Color.White else MaterialTheme.colorScheme.onSurface
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.playlist_songs, playlist.songCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (playlist.artworkUri != null) {
                            Color.White.copy(alpha = 0.78f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Spacer(Modifier.width(AppSpacing.xxs))
                    Icon(
                        Icons.Default.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = if (playlist.artworkUri != null) {
                            Color.White.copy(alpha = 0.78f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}
