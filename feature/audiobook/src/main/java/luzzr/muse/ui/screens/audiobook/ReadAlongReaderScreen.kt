package luzzr.muse.ui.screens.audiobook

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Toc
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import luzzr.muse.domain.model.ReadAlongAnnotation
import luzzr.muse.domain.model.ReadAlongAnnotationColor
import luzzr.muse.domain.model.ReadAlongBookmark
import luzzr.muse.domain.model.ReadAlongFontFamily
import luzzr.muse.domain.model.ReadAlongFontWeight
import luzzr.muse.domain.model.ReadAlongPagerMode
import luzzr.muse.domain.model.ReadAlongSearchHit
import luzzr.muse.domain.model.ReadAlongTheme
import luzzr.muse.domain.model.ReadAlongTocEntry
import luzzr.muse.feature.audiobook.R
import luzzr.muse.ui.animation.MotionReader
import luzzr.muse.ui.components.LocalReduceMotion
import luzzr.muse.ui.components.MuseAlertDialog
import luzzr.muse.ui.components.MuseBottomSheet
import luzzr.muse.ui.components.MuseProgressSlider
import luzzr.muse.ui.theme.AppSpacing
import luzzr.muse.ui.theme.MuseShapeTokens
import java.util.Locale
import kotlin.math.roundToInt

private data class ReadAlongChromePalette(
    val background: Color,
    val surface: Color,
    val content: Color,
    val secondary: Color,
    val accent: Color
)

private fun ReadAlongTheme.chromePalette(): ReadAlongChromePalette = when (this) {
    ReadAlongTheme.PAPER -> ReadAlongChromePalette(
        background = Color(0xFFF4F1EA),
        surface = Color(0xFFEEE9DF),
        content = Color(0xFF3E3A35),
        secondary = Color(0xFF746B60),
        accent = Color(0xFFA6805A)
    )
    ReadAlongTheme.SEPIA -> ReadAlongChromePalette(
        background = Color(0xFFF1E6D0),
        surface = Color(0xFFE9D9BB),
        content = Color(0xFF4B4033),
        secondary = Color(0xFF7B6A55),
        accent = Color(0xFF9B6E42)
    )
    ReadAlongTheme.NIGHT -> ReadAlongChromePalette(
        background = Color(0xFF252321),
        surface = Color(0xFF302D2A),
        content = Color(0xFFE9E0D3),
        secondary = Color(0xFFB7AA9A),
        accent = Color(0xFFD8AF78)
    )
}

@Composable
private fun readerChromePalette(theme: ReadAlongTheme): ReadAlongChromePalette = remember(theme) { theme.chromePalette() }

@Composable
fun ReadAlongRoute(bookId: String, innerPadding: PaddingValues, onBack: () -> Unit, viewModel: ReadAlongViewModel = hiltViewModel()) {
    val state by viewModel.reader.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(bookId, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.beginReading(bookId)
                Lifecycle.Event.ON_STOP -> viewModel.endReading()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            viewModel.beginReading(bookId)
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.endReading()
        }
    }
    val annotations by viewModel.observeAnnotations(bookId).collectAsStateWithLifecycle(initialValue = emptyList())
    val bookmarks by viewModel.observeBookmarks(bookId).collectAsStateWithLifecycle(initialValue = emptyList())
    LaunchedEffect(bookId) { viewModel.openBook(bookId) }
    BackHandler {
        viewModel.saveCurrentProgress()
        onBack()
    }
    ReadAlongScreen(
        state = state,
        annotations = annotations,
        bookmarks = bookmarks,
        outerPadding = innerPadding,
        onBack = {
            viewModel.saveCurrentProgress()
            onBack()
        },
        onTogglePlay = viewModel::togglePlay,
        onSeek = viewModel::seekTo,
        onJumpToText = viewModel::seekToTextRange,
        onTextRangeConsumed = viewModel::clearTextRangeRequest,
        onScrollProgress = viewModel::saveScrollProgress,
        onPageProgress = viewModel::savePageProgress,
        onChapter = viewModel::switchChapter,
        onSentenceSeek = viewModel::seekToSentenceByHref,
        onTocJump = viewModel::seekToChapterByHref,
        onFontScale = viewModel::setFontScale,
        onFontFamily = viewModel::setFontFamily,
        onFontWeight = viewModel::setFontWeight,
        onLineHeight = viewModel::setLineHeightScale,
        onParagraphSpacing = viewModel::setParagraphSpacing,
        onPagerMode = viewModel::setPagerMode,
        onTheme = viewModel::setTheme,
        onAutoFollow = viewModel::setAutoFollow,
        onSpeed = viewModel::setPlaybackSpeed,
        onAddAnnotation = viewModel::addAnnotation,
        onDeleteAnnotation = viewModel::deleteAnnotation,
        onAddBookmark = viewModel::addBookmark,
        onDeleteBookmark = viewModel::deleteBookmark,
        onSearch = { query -> viewModel.searchBook(bookId, query) },
        onSearchHit = viewModel::jumpToSearchHit,
        onCloseSearch = viewModel::clearSearch
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadAlongScreen(
    state: ReadAlongReaderUiState,
    annotations: List<ReadAlongAnnotation>,
    bookmarks: List<ReadAlongBookmark>,
    outerPadding: PaddingValues,
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onJumpToText: (Int, Int) -> Unit,
    onTextRangeConsumed: () -> Unit,
    onScrollProgress: (Float) -> Unit,
    onPageProgress: (Float) -> Unit,
    onChapter: (Int, Boolean) -> Unit,
    onSentenceSeek: (String, String?) -> Unit,
    onTocJump: (String) -> Unit,
    onFontScale: (Float) -> Unit,
    onFontFamily: (luzzr.muse.domain.model.ReadAlongFontFamily) -> Unit,
    onFontWeight: (luzzr.muse.domain.model.ReadAlongFontWeight) -> Unit,
    onLineHeight: (Float) -> Unit,
    onParagraphSpacing: (Float) -> Unit,
    onPagerMode: (luzzr.muse.domain.model.ReadAlongPagerMode) -> Unit,
    onTheme: (ReadAlongTheme) -> Unit,
    onAutoFollow: (Boolean) -> Unit,
    onSpeed: (Float) -> Unit,
    onAddAnnotation: (Int, Int, ReadAlongAnnotationColor, String) -> Unit,
    onDeleteAnnotation: (String) -> Unit,
    onAddBookmark: (String) -> Unit,
    onDeleteBookmark: (String) -> Unit,
    onSearch: (String) -> Unit,
    onSearchHit: (ReadAlongSearchHit) -> Unit,
    onCloseSearch: () -> Unit
) {
    var showToc by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showAnnotations by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var showMoreActions by remember { mutableStateOf(false) }
    var jumpMode by remember { mutableStateOf(false) }
    var chromeVisible by remember { mutableStateOf(true) }
    var followState by remember(state.chapterData?.chapter?.id) {
        mutableStateOf(ReadAlongFollowState())
    }
    var pendingSelection = remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val pendingRange = pendingSelection.value?.let { it.first..it.second }
    val book = state.book
    val chapter = state.chapterData?.chapter
    val chrome = readerChromePalette(state.settings.theme)
    val reduceMotion = LocalReduceMotion.current

    LaunchedEffect(state.requestedTextRange) {
        if (state.requestedTextRange != null) {
            followState = followState.onEvent(ReadAlongFollowEvent.ExplicitJump)
        }
    }

    val previousReadingIndex = chapter?.index?.let { current -> book?.previousReadingChapterIndex(current) }
    val nextReadingIndex = chapter?.index?.let { current -> book?.nextReadingChapterIndex(current) }

    Scaffold(
        modifier = Modifier.fillMaxSize().padding(outerPadding),
        containerColor = chrome.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            AnimatedVisibility(
                visible = chromeVisible,
                enter = if (reduceMotion) EnterTransition.None else MotionReader.chromeEnter,
                exit = if (reduceMotion) ExitTransition.None else MotionReader.chromeExit
            ) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = book?.title ?: stringResource(R.string.readalong_reader_title),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            chapter?.title?.takeIf { it.isNotBlank() }?.let {
                                Text(
                                    text = it,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = chrome.secondary
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.readalong_action_back))
                        }
                    },
                    actions = {
                        IconButton(onClick = { showSearch = true }, enabled = book != null) {
                            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.readalong_reader_search_hint))
                        }
                        IconButton(onClick = { showToc = true }, enabled = book != null) {
                            Icon(Icons.AutoMirrored.Filled.Toc, contentDescription = stringResource(R.string.readalong_action_toc))
                        }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.readalong_action_settings))
                        }
                        Box {
                            IconButton(onClick = { showMoreActions = true }, enabled = book != null) {
                                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.readalong_action_more))
                            }
                            DropdownMenu(
                                expanded = showMoreActions,
                                onDismissRequest = { showMoreActions = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.readalong_annotations_title)) },
                                    onClick = {
                                        showMoreActions = false
                                        showAnnotations = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.readalong_bookmarks_title)) },
                                    onClick = {
                                        showMoreActions = false
                                        showBookmarks = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(
                                                if (jumpMode) {
                                                    R.string.readalong_action_close_jump
                                                } else {
                                                    R.string.readalong_action_jump_text
                                                }
                                            )
                                        )
                                    },
                                    enabled = chapter != null,
                                    onClick = {
                                        showMoreActions = false
                                        jumpMode = !jumpMode
                                    }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = chrome.background,
                        scrolledContainerColor = chrome.surface,
                        navigationIconContentColor = chrome.content,
                        titleContentColor = chrome.content,
                        actionIconContentColor = chrome.content
                    )
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = chromeVisible,
                enter = if (reduceMotion) EnterTransition.None else MotionReader.controlsEnter,
                exit = if (reduceMotion) ExitTransition.None else MotionReader.controlsExit
            ) {
                ReadAlongControls(
                    state = state,
                    onTogglePlay = onTogglePlay,
                    onSeek = {
                        followState = followState.onEvent(ReadAlongFollowEvent.ExplicitJump)
                        onSeek(it)
                    },
                    canPrevious = previousReadingIndex != null,
                    canNext = nextReadingIndex != null,
                    onPrevious = {
                        previousReadingIndex?.let {
                            followState = followState.onEvent(ReadAlongFollowEvent.ExplicitJump)
                            onChapter(it, true)
                        }
                    },
                    onNext = {
                        nextReadingIndex?.let {
                            followState = followState.onEvent(ReadAlongFollowEvent.ExplicitJump)
                            onChapter(it, true)
                        }
                    },
                    onAddBookmark = { onAddBookmark("") }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(chrome.background)
        ) {
            when {
                state.isLoading -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp))
                state.error != null -> Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = state.error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.size(AppSpacing.md))
                    Button(onClick = onBack) { Text(stringResource(R.string.readalong_reader_error_back)) }
                }
                state.chapterData != null && chapter != null -> Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    ReadAlongChapterView(
                        chapterData = state.chapterData,
                        textIndex = state.textIndex,
                        activeUnitIndex = state.activeUnitIndex,
                        activeSentenceIndex = state.activeSentenceIndex,
                        targetTextRange = state.requestedTextRange,
                        settings = state.settings,
                        annotations = annotations,
                        scrollProgress = state.progress.scrollProgress,
                        pageProgress = state.progress.pageProgress,
                        onScrollProgress = onScrollProgress,
                        onPageProgress = onPageProgress,
                        onTextRangeConsumed = onTextRangeConsumed,
                        onReaderTap = { chromeVisible = !chromeVisible },
                        onFollowSuspended = {
                            followState = followState.onEvent(ReadAlongFollowEvent.UserInteraction)
                        },
                        onSeekToSentence = { href, elementId, _ ->
                            followState = followState.onEvent(ReadAlongFollowEvent.ExplicitJump)
                            onSentenceSeek(href, elementId)
                        },
                        onLongPressSelection = { start, end ->
                            if (jumpMode) {
                                onJumpToText(start, end)
                                jumpMode = false
                            } else {
                                pendingSelection.value = start to end
                            }
                        },
                        jumpMode = jumpMode,
                        chromeVisible = chromeVisible,
                        resumeFollowRequest = followState.resumeRequest,
                        modifier = Modifier.fillMaxSize()
                    )
                    AnimatedVisibility(
                        visible = followState.suspended && state.settings.autoFollow,
                        enter = if (reduceMotion) EnterTransition.None else MotionReader.controlsEnter,
                        exit = if (reduceMotion) ExitTransition.None else MotionReader.controlsExit,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = AppSpacing.lg)
                    ) {
                        Surface(
                            onClick = {
                                followState = followState.onEvent(ReadAlongFollowEvent.Resume)
                            },
                            shape = MuseShapeTokens.Pill,
                            color = chrome.surface.copy(alpha = 0.96f),
                            contentColor = chrome.accent,
                            shadowElevation = 3.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(
                                    horizontal = AppSpacing.md,
                                    vertical = AppSpacing.xs
                                ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.GraphicEq,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(AppSpacing.xs))
                                Text(
                                    text = "回到当前朗读",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
                else -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.readalong_reader_loading),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showToc && book != null) {
        MuseBottomSheet(onDismiss = { showToc = false }, title = stringResource(R.string.readalong_reader_toc_title)) {
            TableOfContents(
                book = book,
                currentChapterId = chapter?.id,
                onSelect = { entry ->
                    showToc = false
                    onTocJump(entry.href)
                }
            )
        }
    }

    if (showSettings) {
        MuseBottomSheet(onDismiss = { showSettings = false }, title = stringResource(R.string.readalong_reader_settings_title)) {
            ReaderSettingsSheet(
                state = state,
                onFontScale = onFontScale,
                onFontFamily = onFontFamily,
                onFontWeight = onFontWeight,
                onLineHeight = onLineHeight,
                onParagraphSpacing = onParagraphSpacing,
                onPagerMode = onPagerMode,
                onTheme = onTheme,
                onAutoFollow = onAutoFollow,
                onSpeed = onSpeed
            )
        }
    }

    if (showSearch) {
        SearchSheet(
            active = state.searchActive,
            results = state.searchResults,
            onSearch = onSearch,
            onPick = {
                onSearchHit(it)
                showSearch = false
            },
            onDismiss = {
                onCloseSearch()
                showSearch = false
            }
        )
    }

    if (showAnnotations) {
        AnnotationsSheet(
            annotations = annotations,
            onDelete = onDeleteAnnotation,
            onDismiss = { showAnnotations = false }
        )
    }

    if (showBookmarks) {
        BookmarksSheet(
            bookmarks = bookmarks,
            onPick = {
                onTocJump(it.chapterHref)
                showBookmarks = false
            },
            onDelete = onDeleteBookmark,
            onDismiss = { showBookmarks = false }
        )
    }

    pendingRange?.let { range ->
        AnnotationComposerDialog(
            range = range,
            onConfirm = { color, note ->
                onAddAnnotation(range.first, range.last + 1, color, note)
                pendingSelection.value = null
            },
            onDismiss = { pendingSelection.value = null }
        )
    }
}

@Composable
private fun ReadAlongControls(
    state: ReadAlongReaderUiState,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    canPrevious: Boolean,
    canNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onAddBookmark: () -> Unit
) {
    val duration = state.playbackDurationMs
    val position = state.playbackPositionMs.coerceIn(0L, duration.coerceAtLeast(0L))
    val book = state.book
    val chapter = state.chapterData?.chapter
    val chrome = readerChromePalette(state.settings.theme)
    Surface(
        color = chrome.surface,
        contentColor = chrome.content,
        tonalElevation = 0.dp,
        shape = MuseShapeTokens.Sheet
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.xs)
        ) {
            if (book != null && chapter != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.GraphicEq,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = chrome.accent
                    )
                    Spacer(Modifier.width(AppSpacing.xxs))
                    Text(
                        text = stringResource(
                            R.string.readalong_chapter_listening,
                            book.readingChapterOrdinal(chapter.index) ?: (chapter.index + 1).coerceAtLeast(1),
                            book.readingChapterCount
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = chrome.secondary
                    )
                    Spacer(Modifier.width(AppSpacing.sm))
                    state.remainingMinutes?.let {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.HourglassEmpty,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = chrome.secondary
                            )
                            Spacer(Modifier.width(AppSpacing.xxs))
                            Text(
                                text = stringResource(R.string.readalong_reader_remaining_minutes, it),
                                style = MaterialTheme.typography.labelSmall,
                                color = chrome.secondary
                            )
                        }
                    }
                }
                Spacer(Modifier.height(AppSpacing.xxs))
            }
            if (duration > 0L) {
                MuseProgressSlider(
                    value = position.toFloat() / duration.toFloat(),
                    onValueChange = { value -> onSeek((value * duration).roundToInt().toLong()) },
                    onValueChangeFinished = {},
                    activeColor = chrome.accent,
                    inactiveColor = chrome.secondary.copy(alpha = 0.28f),
                    thumbColor = chrome.accent,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatDuration(position), style = MaterialTheme.typography.labelSmall, color = chrome.secondary)
                    Text(formatDuration(duration), style = MaterialTheme.typography.labelSmall, color = chrome.secondary)
                }
            } else {
                Text(
                    text = stringResource(R.string.readalong_reader_no_audio),
                    style = MaterialTheme.typography.labelSmall,
                    color = chrome.secondary
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = AppSpacing.xxs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = { onSeek((position - 10_000L).coerceAtLeast(0L)) }, enabled = duration > 0) {
                    Icon(
                        Icons.Default.Replay10,
                        contentDescription = stringResource(R.string.readalong_action_seek_back),
                        tint = chrome.content
                    )
                }
                IconButton(onClick = onPrevious, enabled = canPrevious) {
                    Icon(
                        Icons.Default.SkipPrevious,
                        contentDescription = stringResource(R.string.readalong_action_prev_chapter),
                        tint = chrome.content
                    )
                }
                val hasAudio = !chapter?.audioPath.isNullOrBlank()
                Surface(
                    onClick = { if (hasAudio) onTogglePlay() },
                    shape = CircleShape,
                    color = if (hasAudio) chrome.accent else chrome.secondary.copy(alpha = 0.22f)
                ) {
                    Box(
                        modifier = Modifier.size(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = stringResource(
                                if (state.isPlaying) {
                                    R.string.readalong_action_pause
                                } else {
                                    R.string.readalong_action_play
                                }
                            ),
                            tint = if (hasAudio) chrome.background else chrome.secondary
                        )
                    }
                }
                IconButton(
                    onClick = onNext,
                    enabled = canNext
                ) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = stringResource(R.string.readalong_action_next_chapter),
                        tint = chrome.content
                    )
                }
                IconButton(onClick = { onSeek((position + 10_000L).coerceAtMost(duration)) }, enabled = duration > 0) {
                    Icon(
                        Icons.Default.Forward10,
                        contentDescription = stringResource(R.string.readalong_action_seek_forward),
                        tint = chrome.content
                    )
                }
                IconButton(onClick = onAddBookmark) {
                    Icon(
                        Icons.Default.BookmarkAdd,
                        contentDescription = stringResource(R.string.readalong_bookmarks_add),
                        tint = chrome.content
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TableOfContents(book: luzzr.muse.domain.model.ReadAlongBook, currentChapterId: String?, onSelect: (ReadAlongTocEntry) -> Unit) {
    val toc = book.toc
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.sm)
    ) {
        Text(
            text = stringResource(R.string.readalong_reader_toc_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.size(AppSpacing.sm))
        if (toc.isEmpty()) {
            Text(
                text = stringResource(R.string.readalong_reader_toc_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(toc, key = { it.id }) { entry ->
                    val isCurrent = book.chapters.getOrNull(entry.chapterIndex)?.id == currentChapterId
                    Surface(
                        onClick = { onSelect(entry) },
                        color = if (isCurrent) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            Color.Transparent
                        },
                        shape = MuseShapeTokens.Item
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = (entry.depth * AppSpacing.md.value).dp,
                                    end = AppSpacing.sm,
                                    top = 10.dp,
                                    bottom = 10.dp
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isCurrent) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(AppSpacing.xs))
                            }
                            Text(
                                text = entry.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isCurrent) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderSettingsSheet(
    state: ReadAlongReaderUiState,
    onFontScale: (Float) -> Unit,
    onFontFamily: (ReadAlongFontFamily) -> Unit,
    onFontWeight: (ReadAlongFontWeight) -> Unit,
    onLineHeight: (Float) -> Unit,
    onParagraphSpacing: (Float) -> Unit,
    onPagerMode: (ReadAlongPagerMode) -> Unit,
    onTheme: (ReadAlongTheme) -> Unit,
    onAutoFollow: (Boolean) -> Unit,
    onSpeed: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.md),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Settings, contentDescription = null)
            Spacer(Modifier.width(AppSpacing.sm))
            Text(
                text = stringResource(R.string.readalong_reader_settings_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
        Text(
            text = stringResource(
                R.string.readalong_reader_settings_font,
                (state.settings.fontScale * 100).roundToInt()
            ),
            style = MaterialTheme.typography.labelLarge
        )
        Slider(value = state.settings.fontScale, onValueChange = onFontScale, valueRange = 0.6f..2.0f)
        Text(stringResource(R.string.readalong_reader_font_family), style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
        ) {
            ReadAlongFontFamily.values().forEach { family ->
                FilterChip(
                    selected = state.settings.fontFamily == family,
                    onClick = { onFontFamily(family) },
                    label = { Text(fontFamilyLabel(family)) }
                )
            }
        }
        Text(stringResource(R.string.readalong_reader_font_weight), style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
        ) {
            ReadAlongFontWeight.values().forEach { weight ->
                FilterChip(
                    selected = state.settings.fontWeight == weight,
                    onClick = { onFontWeight(weight) },
                    label = { Text(fontWeightLabel(weight)) }
                )
            }
        }
        Text(
            text = stringResource(
                R.string.readalong_reader_settings_line_height,
                state.settings.lineHeightScale.formatOneDecimal()
            ),
            style = MaterialTheme.typography.labelLarge
        )
        Slider(value = state.settings.lineHeightScale, onValueChange = onLineHeight, valueRange = 1.1f..2.6f)
        Text(
            text = stringResource(
                R.string.readalong_reader_paragraph_spacing,
                state.settings.paragraphSpacing.formatOneDecimal()
            ),
            style = MaterialTheme.typography.labelLarge
        )
        Slider(value = state.settings.paragraphSpacing, onValueChange = onParagraphSpacing, valueRange = 0.5f..2.5f)
        Text(stringResource(R.string.readalong_reader_pager_mode), style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
        ) {
            FilterChip(
                selected = state.settings.pagerMode == ReadAlongPagerMode.SCROLL,
                onClick = { onPagerMode(ReadAlongPagerMode.SCROLL) },
                label = { Text(stringResource(R.string.readalong_reader_pager_scroll)) }
            )
            FilterChip(
                selected = state.settings.pagerMode == ReadAlongPagerMode.PAGED,
                onClick = { onPagerMode(ReadAlongPagerMode.PAGED) },
                label = { Text(stringResource(R.string.readalong_reader_pager_paged)) }
            )
        }
        Text(stringResource(R.string.readalong_reader_settings_theme), style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
        ) {
            ReadAlongTheme.values().forEach { theme ->
                FilterChip(
                    selected = state.settings.theme == theme,
                    onClick = { onTheme(theme) },
                    label = { Text(themeLabel(theme)) }
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Speed, contentDescription = null)
            Spacer(Modifier.width(AppSpacing.xs))
            Text(
                text = stringResource(
                    R.string.readalong_reader_settings_speed,
                    state.settings.playbackSpeed.formatOneDecimal() + "x"
                ),
                style = MaterialTheme.typography.labelLarge
            )
        }
        Slider(value = state.settings.playbackSpeed, onValueChange = onSpeed, valueRange = 0.5f..3f)
        FilterChip(
            selected = state.settings.autoFollow,
            onClick = { onAutoFollow(!state.settings.autoFollow) },
            label = { Text(stringResource(R.string.readalong_reader_settings_auto_follow)) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchSheet(
    active: Boolean,
    results: List<ReadAlongSearchHit>,
    onSearch: (String) -> Unit,
    onPick: (ReadAlongSearchHit) -> Unit,
    onDismiss: () -> Unit
) {
    MuseBottomSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
        ) {
            var query by remember { mutableStateOf("") }
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    onSearch(it)
                },
                label = { Text(stringResource(R.string.readalong_reader_search_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (results.isEmpty() && active && query.isNotBlank()) {
                Text(
                    text = stringResource(R.string.readalong_reader_search_no_results),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (results.isNotEmpty()) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(results) { hit ->
                        Surface(
                            onClick = { onPick(hit) },
                            shape = MuseShapeTokens.Item,
                            color = MaterialTheme.colorScheme.surfaceContainerLow
                        ) {
                            Column(modifier = Modifier.padding(horizontal = AppSpacing.sm, vertical = 8.dp)) {
                                Text(hit.excerpt, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    text = "${hit.bookId}/${hit.chapterId}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnnotationsSheet(annotations: List<ReadAlongAnnotation>, onDelete: (String) -> Unit, onDismiss: () -> Unit) {
    MuseBottomSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
        ) {
            Text(
                text = stringResource(R.string.readalong_annotations_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            if (annotations.isEmpty()) {
                Text(stringResource(R.string.readalong_annotations_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(annotations, key = { it.id }) { ann ->
                        Surface(shape = MuseShapeTokens.Item, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                            Column(modifier = Modifier.padding(AppSpacing.sm)) {
                                Text("「${ann.quote}」", style = MaterialTheme.typography.bodyMedium, maxLines = 3)
                                if (ann.note.isNotBlank()) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = ann.note,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                    TextButton(onClick = { onDelete(ann.id) }) {
                                        Text("删除", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookmarksSheet(
    bookmarks: List<ReadAlongBookmark>,
    onPick: (ReadAlongBookmark) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit
) {
    MuseBottomSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
        ) {
            Text(
                text = stringResource(R.string.readalong_bookmarks_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            if (bookmarks.isEmpty()) {
                Text(stringResource(R.string.readalong_bookmarks_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(bookmarks, key = { it.id }) { bookmark ->
                        Surface(
                            onClick = { onPick(bookmark) },
                            shape = MuseShapeTokens.Item,
                            color = MaterialTheme.colorScheme.surfaceContainerLow
                        ) {
                            Column(modifier = Modifier.padding(AppSpacing.sm)) {
                                Text(bookmark.label, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = formatDuration(bookmark.audioPositionMs),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                    TextButton(onClick = { onDelete(bookmark.id) }) {
                                        Text("删除", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnnotationComposerDialog(range: IntRange, onConfirm: (ReadAlongAnnotationColor, String) -> Unit, onDismiss: () -> Unit) {
    var color by remember { mutableStateOf(ReadAlongAnnotationColor.YELLOW) }
    var note by remember { mutableStateOf("") }
    MuseAlertDialog(
        onDismiss = onDismiss,
        title = stringResource(R.string.readalong_annotations_add),
        confirmLabel = "保存",
        dismissLabel = stringResource(R.string.readalong_shelf_delete_cancel),
        onConfirm = { onConfirm(color, note) },
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                Text(
                    text = "选区 ${range.first}-${range.last}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
                ) {
                    ReadAlongAnnotationColor.values().forEach { c ->
                        FilterChip(
                            selected = color == c,
                            onClick = { color = c },
                            label = { Text(colorLabel(c)) }
                        )
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("笔记") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    )
}

private fun colorLabel(color: ReadAlongAnnotationColor): String = when (color) {
    ReadAlongAnnotationColor.YELLOW -> "黄"
    ReadAlongAnnotationColor.GREEN -> "绿"
    ReadAlongAnnotationColor.PINK -> "粉"
    ReadAlongAnnotationColor.BLUE -> "蓝"
    ReadAlongAnnotationColor.UNDERLINE -> "线"
}

private fun fontFamilyLabel(family: ReadAlongFontFamily): String = when (family) {
    ReadAlongFontFamily.BOOK -> "书籍"
    ReadAlongFontFamily.SERIF -> "衬线"
    ReadAlongFontFamily.SANS -> "无衬线"
    ReadAlongFontFamily.MONO -> "等宽"
    ReadAlongFontFamily.SYSTEM -> "系统"
}

private fun fontWeightLabel(weight: ReadAlongFontWeight): String = when (weight) {
    ReadAlongFontWeight.REGULAR -> "细"
    ReadAlongFontWeight.MEDIUM -> "中"
    ReadAlongFontWeight.SEMIBOLD -> "粗"
}

private fun themeLabel(theme: ReadAlongTheme): String = when (theme) {
    ReadAlongTheme.PAPER -> "米纸"
    ReadAlongTheme.SEPIA -> "暖棕"
    ReadAlongTheme.NIGHT -> "夜间"
}

private fun formatDuration(value: Long): String {
    val totalSeconds = (value / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}

private fun Float.formatOneDecimal(): String = String.format(Locale.getDefault(), "%.1f", this)
