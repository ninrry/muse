package luzzr.muse.ui.screens.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOneOn
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import luzzr.muse.data.model.Song
import luzzr.muse.player.SleepTimerMode
import luzzr.muse.ui.animation.MotionDuration
import luzzr.muse.ui.components.DefaultAlbumCover
import luzzr.muse.ui.components.LyricsEmptyState
import luzzr.muse.ui.components.LyricsLoadingState
import luzzr.muse.ui.components.LyricsView
import luzzr.muse.ui.haptic.HapticUtil
import luzzr.muse.ui.haptic.pressScale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel = viewModel(),
    innerPadding: PaddingValues = PaddingValues(),
    onBack: () -> Unit = {}
) {
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val duration by viewModel.duration.collectAsStateWithLifecycle()
    val repeatMode by viewModel.repeatMode.collectAsStateWithLifecycle()
    val shuffleMode by viewModel.shuffleMode.collectAsStateWithLifecycle()
    val playlist by viewModel.currentPlaylist.collectAsStateWithLifecycle()

    // Lyrics state
    val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
    val currentLyricLine by viewModel.currentLyricLine.collectAsStateWithLifecycle()
    val lineProgress by viewModel.lineProgress.collectAsStateWithLifecycle()
    val lyricsLoading by viewModel.lyricsLoading.collectAsStateWithLifecycle()
    val lyricsError by viewModel.lyricsError.collectAsStateWithLifecycle()

    // Sleep timer state
    val sleepTimerMode by viewModel.sleepTimer.activeMode.collectAsStateWithLifecycle()
    val sleepTimerRemaining by viewModel.sleepTimer.remainingMs.collectAsStateWithLifecycle()

    var showQueue by remember { mutableStateOf(false) }
    var showSleepTimer by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    // Lyrics offset control: hidden by default, toggled via indicator
    var showLyricsOffset by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(if (showLyrics) "歌词" else "正在播放") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ExpandMore, contentDescription = "收起")
                    }
                },
                actions = {
                    // Lyrics toggle (icon-only)
                    IconButton(
                        onClick = { showLyrics = !showLyrics },
                        modifier = Modifier.semantics {
                            contentDescription = if (showLyrics) "切换到封面" else "切换到歌词"
                        }
                    ) {
                        Icon(
                            imageVector = if (showLyrics) Icons.Default.Image else Icons.Default.Article,
                            contentDescription = null,
                            tint = if (showLyrics) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // Refresh lyrics — only visible in lyrics mode
                    if (showLyrics) {
                        IconButton(
                            onClick = { viewModel.resetLyrics() }
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "刷新歌词",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = { showQueue = true }) {
                        Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "播放队列")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        if (currentSong == null) {
            EmptyPlayerState(modifier = Modifier.padding(padding))
            return@Scaffold
        }

        val song = currentSong!!

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            if (showLyrics) {
                // === LYRICS MODE ===
                CompactSongHeader(
                    song = song,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )

                // Offset toggle row: shows current offset as chip, toggles control panel
                LyricsOffsetToggle(
                    offsetMs = viewModel.lyricsOffsetMs.collectAsStateWithLifecycle().value,
                    isExpanded = showLyricsOffset,
                    onToggle = { showLyricsOffset = !showLyricsOffset },
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp)
                )

                // Collapsible offset control panel
                AnimatedVisibility(
                    visible = showLyricsOffset,
                    enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                    exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
                ) {
                    LyricsOffsetControl(
                        offsetMs = viewModel.lyricsOffsetMs.collectAsStateWithLifecycle().value,
                        onAdjust = { viewModel.adjustLyricsOffset(it) },
                        onReset = { viewModel.resetLyricsOffset() },
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp)
                    )
                }

                // Lyrics content fills remaining space
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        lyrics.isNotEmpty() -> {
                            LyricsView(
                                lyrics = lyrics,
                                currentLineIndex = currentLyricLine,
                                lineProgress = lineProgress,
                                onSeek = { viewModel.seekTo(it) },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        lyricsLoading -> {
                            LyricsLoadingState()
                        }
                        else -> {
                            LyricsEmptyState(
                                message = lyricsError ?: "暂无歌词"
                            )
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
            } else {
                // === NORMAL MODE (scrollable) ===
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(16.dp))

                    AlbumArtSection(song = song, isPlaying = isPlaying)
                    Spacer(Modifier.height(16.dp))
                    SongInfoSection(song = song)
                    Spacer(Modifier.height(8.dp))
                }
            }

            // === PLAYBACK CONTROLS (always visible at bottom) ===
            PlaybackControls(
                modifier = Modifier.padding(horizontal = 24.dp),
                song = song,
                duration = duration,
                progress = progress,
                isPlaying = isPlaying,
                repeatMode = repeatMode,
                shuffleMode = shuffleMode,
                sleepTimerMode = sleepTimerMode,
                sleepTimerRemaining = sleepTimerRemaining,
                onSeek = { viewModel.seekTo(it) },
                onTogglePlayPause = { viewModel.togglePlayPause() },
                onSkipNext = { viewModel.skipToNext() },
                onSkipPrevious = { viewModel.skipToPrevious() },
                onCycleRepeat = { viewModel.cycleRepeatMode() },
                onToggleShuffle = { viewModel.toggleShuffle() },
                onShowSleepTimer = { showSleepTimer = true },
                onShowQueue = { showQueue = true },
                sleepTimerFormat = { viewModel.sleepTimer.formatRemaining() },
                viewModel = viewModel
            )

            Spacer(Modifier.height(12.dp))
        }
    }

    // Queue bottom sheet
    if (showQueue) {
        QueueBottomSheet(
            playlist = playlist,
            currentSong = currentSong,
            onPlaySong = { viewModel.playSongAtIndex(it) },
            onDismiss = { showQueue = false }
        )
    }

    // Sleep timer dialog
    if (showSleepTimer) {
        SleepTimerDialog(
            currentMode = sleepTimerMode,
            onSelect = { mode ->
                if (mode == SleepTimerMode.OFF) {
                    viewModel.stopSleepTimer()
                } else {
                    viewModel.startSleepTimer(mode)
                }
                showSleepTimer = false
            },
            onDismiss = { showSleepTimer = false }
        )
    }
}

@Composable
private fun EmptyPlayerState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "未在播放",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "从首页或曲库中选择歌曲播放",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }
    }
}

@Composable
private fun AlbumArtSection(song: Song, isPlaying: Boolean) {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val coverSize = screenWidth * 0.6f

    Crossfade(
        targetState = song.id,
        animationSpec = tween(MotionDuration.medium2),
        label = "album_art_crossfade"
    ) { _ ->
        Surface(
            modifier = Modifier
                .size(coverSize)
                .then(if (!isPlaying) Modifier.alpha(0.7f) else Modifier),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            tonalElevation = 4.dp
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                DefaultAlbumCover(
                    placeholder = song.title.take(1).uppercase(),
                    modifier = Modifier.fillMaxSize()
                )
                if (song.artworkUri != null) {
                    AsyncImage(
                        model = song.artworkUri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun SongInfoSection(song: Song) {
    AnimatedContent(
        targetState = song.id,
        transitionSpec = {
            ContentTransform(
                targetContentEnter = fadeIn(animationSpec = tween(200)) +
                    scaleIn(initialScale = 0.95f, animationSpec = tween(200)),
                initialContentExit = fadeOut(animationSpec = tween(100)) +
                    scaleOut(targetScale = 0.95f, animationSpec = tween(100)),
            )
        },
        label = "song_info"
    ) { _ ->
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CompactSongHeader(song: Song, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(56.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            tonalElevation = 2.dp
        ) {
            Box {
                DefaultAlbumCover(
                    placeholder = song.title.take(1).uppercase(),
                    modifier = Modifier.fillMaxSize()
                )
                if (song.artworkUri != null) {
                    AsyncImage(
                        model = song.artworkUri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }

        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PlaybackControls(
    modifier: Modifier = Modifier,
    song: Song,
    duration: Long,
    progress: Long,
    isPlaying: Boolean,
    repeatMode: Int,
    shuffleMode: Boolean,
    sleepTimerMode: SleepTimerMode?,
    sleepTimerRemaining: Long?,
    onSeek: (Long) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit,
    onShowSleepTimer: () -> Unit,
    onShowQueue: () -> Unit,
    sleepTimerFormat: () -> String,
    viewModel: PlayerViewModel
) {
    Column(modifier = modifier.fillMaxWidth()) {
        val sliderValue = if (duration > 0) (progress.toFloat() / duration).coerceIn(0f, 1f) else 0f
        var sliderDragging by remember { mutableStateOf(false) }
        var dragValue by remember { mutableStateOf(sliderValue) }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            contentAlignment = Alignment.Center
        ) {
            Slider(
                value = if (sliderDragging) dragValue else sliderValue,
                onValueChange = {
                    sliderDragging = true
                    dragValue = it
                },
                onValueChangeFinished = {
                    sliderDragging = false
                    onSeek((dragValue * duration).toLong())
                },
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(formatTime(progress), style = MaterialTheme.typography.bodySmall)
            Text(formatTime(duration), style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(8.dp))

        // Transport controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val repeatActive = repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF
            val repeatTransition = updateTransition(targetState = repeatActive, label = "repeat")
            val repeatTint by repeatTransition.animateColor(
                transitionSpec = { tween(MotionDuration.medium1) },
                label = "repeat_tint"
            ) { active ->
                if (active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            }
            IconButton(onClick = onCycleRepeat) {
                Icon(
                    when (repeatMode) {
                        androidx.media3.common.Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOneOn
                        else -> Icons.Default.Repeat
                    },
                    contentDescription = "重复模式",
                    tint = repeatTint
                )
            }

            IconButton(onClick = onSkipPrevious) {
                Icon(
                    Icons.Default.SkipPrevious,
                    contentDescription = "上一首",
                    modifier = Modifier.size(36.dp)
                )
            }

            val playInteractionSource = remember { MutableInteractionSource() }
            val context = LocalContext.current
            Surface(
                onClick = {
                    HapticUtil.vibrate(context)
                    onTogglePlayPause()
                },
                interactionSource = playInteractionSource,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(64.dp)
                    .pressScale(playInteractionSource, 0.92f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Crossfade(
                        targetState = isPlaying,
                        animationSpec = tween(MotionDuration.medium1),
                        label = "play_pause"
                    ) { playing ->
                        Icon(
                            if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playing) "暂停" else "播放",
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }

            IconButton(onClick = onSkipNext) {
                Icon(
                    Icons.Default.SkipNext,
                    contentDescription = "下一首",
                    modifier = Modifier.size(36.dp)
                )
            }

            val shuffleTransition = updateTransition(targetState = shuffleMode, label = "shuffle")
            val shuffleTint by shuffleTransition.animateColor(
                transitionSpec = { tween(MotionDuration.medium1) },
                label = "shuffle_tint"
            ) { enabled ->
                if (enabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            }
            IconButton(onClick = onToggleShuffle) {
                Icon(
                    Icons.Default.Shuffle,
                    contentDescription = "随机播放",
                    tint = shuffleTint
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Bottom row: sleep timer + queue
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            TextButton(onClick = onShowSleepTimer) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (sleepTimerMode != null) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    if (sleepTimerMode != null && sleepTimerRemaining != null)
                        "定时关闭 ${sleepTimerFormat()}"
                    else
                        "定时关闭"
                )
            }

            Spacer(Modifier.width(16.dp))

            TextButton(onClick = onShowQueue) {
                Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(4.dp))
                Text("播放队列")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueBottomSheet(
    playlist: List<Song>,
    currentSong: Song?,
    onPlaySong: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                "播放队列",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (playlist.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("队列为空", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    itemsIndexed(
                        items = playlist,
                        key = { _, item -> item.id }
                    ) { index, item ->
                        val isCurrent = item.id == currentSong?.id
                        Surface(
                            onClick = { onPlaySong(index) },
                            color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${if (isCurrent) "▶ " else ""}${item.title} - ${item.artist}",
                                    style = if (isCurrent) MaterialTheme.typography.bodyMedium
                                            else MaterialTheme.typography.bodySmall,
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    item.formattedDuration,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

/**
 * Compact toggle indicator for lyrics offset.
 * - Offset = 0: shows faint "调" chip to reveal controls
 * - Offset ≠ 0: shows current offset (±Xs) pill, tap to reveal controls
 */
@Composable
private fun LyricsOffsetToggle(
    offsetMs: Long,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isAdjusted = offsetMs != 0L
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            onClick = onToggle,
            shape = RoundedCornerShape(8.dp),
            color = if (isAdjusted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            tonalElevation = 0.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isAdjusted) "%+.2fs".format(offsetMs / 1000f) else "调",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isAdjusted) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                // Small expand arrow
                if (!isExpanded) {
                    Text(
                        text = " ▾",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isAdjusted) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                } else {
                    Text(
                        text = " ▴",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

/**
 * Full lyrics timing offset control panel.
 * Provides fine-grained adjustments: -1s, -0.5s, -0.1s, +0.1s, +0.5s, +1s.
 * Displayed when the user taps the offset toggle chip.
 */
@Composable
private fun LyricsOffsetControl(
    offsetMs: Long,
    onAdjust: (Long) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isAdjusted = offsetMs != 0L
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // Rewind buttons
            OffsetButton("-1s", onClick = { onAdjust(-1000L) })
            Spacer(Modifier.width(4.dp))
            OffsetButton("-0.5s", onClick = { onAdjust(-500L) })
            Spacer(Modifier.width(4.dp))
            OffsetButton("-0.1s", onClick = { onAdjust(-100L) })

            // Reset button between rewind and forward
            if (isAdjusted) {
                Spacer(Modifier.width(6.dp))
                TextButton(
                    onClick = onReset,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text("重置", style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.width(6.dp))
            } else {
                Spacer(Modifier.width(4.dp))
            }

            // Forward buttons
            OffsetButton("+0.1s", onClick = { onAdjust(100L) })
            Spacer(Modifier.width(4.dp))
            OffsetButton("+0.5s", onClick = { onAdjust(500L) })
            Spacer(Modifier.width(4.dp))
            OffsetButton("+1s", onClick = { onAdjust(1000L) })
        }
    }
}

/**
 * Small label button for a single offset adjustment.
 */
@Composable
private fun OffsetButton(
    label: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        tonalElevation = 0.dp
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
