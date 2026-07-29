package luzzr.muse.ui

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import luzzr.muse.MainViewModel
import luzzr.muse.R
import luzzr.muse.domain.model.Song
import luzzr.muse.ui.animation.MotionMiniPlayer
import luzzr.muse.ui.animation.MotionNav
import luzzr.muse.ui.components.LocalReduceMotion
import luzzr.muse.ui.components.MiniPlayer
import luzzr.muse.ui.navigation.Screen
import luzzr.muse.ui.theme.AppSpacing
import luzzr.muse.ui.theme.LocalMuseVisualStyle
import luzzr.muse.ui.theme.MuseDimens
import luzzr.muse.ui.theme.MuseShapeTokens
import luzzr.muse.ui.theme.WindowSize
import luzzr.muse.ui.theme.currentWindowSize

data class NavItem(
    val screen: Screen,
    @StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val navItems = listOf(
    NavItem(Screen.Home, R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home),
    NavItem(Screen.Library, R.string.nav_library, Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic),
    NavItem(
        Screen.Audiobook,
        R.string.nav_audiobook,
        Icons.AutoMirrored.Filled.MenuBook,
        Icons.AutoMirrored.Outlined.MenuBook
    ),
    NavItem(Screen.Settings, R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings)
)

@Composable
fun MuseScaffold(
    viewModel: MainViewModel,
    hasAudioPermission: Boolean,
    hasNotificationPermission: Boolean = true,
    onRequestPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit = {}
) {
    val navController = rememberNavController()
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val progressProvider = remember(viewModel) { { viewModel.progressRatio() } }
    val shuffleMode by viewModel.shuffleMode.collectAsStateWithLifecycle()
    val isAudiobookVisible by viewModel.isAudiobookVisible.collectAsStateWithLifecycle()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route
    val isPlayerScreen = Screen.Player.isPlayerRoute(currentRoute)
    val isReadAlongScreen = Screen.ReadAlong.isReadAlongRoute(currentRoute)
    val showAppChrome = !isPlayerScreen && !isReadAlongScreen
    val reduceMotion = LocalReduceMotion.current
    val windowSize = currentWindowSize()
    val visibleNavItems = remember(isAudiobookVisible) {
        navItems.filter { it.screen != Screen.Audiobook || isAudiobookVisible }
    }

    LaunchedEffect(isAudiobookVisible, currentRoute) {
        if (!isAudiobookVisible && currentRoute == Screen.Audiobook.route) {
            navController.navigate(Screen.Settings.route) {
                popUpTo(Screen.Audiobook.route) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    val navigateTo: (Screen) -> Unit = { screen ->
        navController.navigate(screen.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    val miniPlayer: @Composable () -> Unit = {
        MuseMiniPlayerChrome(
            currentSong = currentSong,
            isPlaying = isPlaying,
            shuffleMode = shuffleMode,
            progressProvider = progressProvider,
            reduceMotion = reduceMotion,
            onTogglePlayPause = viewModel::togglePlayPause,
            onOpenPlayer = {
                navController.navigate(Screen.Player.route) { launchSingleTop = true }
            },
            onOpenQueue = {
                navController.navigate(Screen.Player.QUEUE_ROUTE) { launchSingleTop = true }
            }
        )
    }
    val navContent: @Composable (PaddingValues) -> Unit = { innerPadding ->
        MuseNavHost(
            navController = navController,
            innerPadding = innerPadding,
            hasAudioPermission = hasAudioPermission,
            hasNotificationPermission = hasNotificationPermission,
            onRequestPermission = onRequestPermission,
            onRequestNotificationPermission = onRequestNotificationPermission,
            showAudiobook = isAudiobookVisible
        )
    }

    if (windowSize == WindowSize.Compact || !showAppChrome) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (showAppChrome) {
                    MuseCompactDock(
                        items = visibleNavItems,
                        currentRoute = currentRoute,
                        onNavigate = navigateTo,
                        miniPlayer = miniPlayer
                    )
                }
            },
            content = navContent
        )
    } else {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            MuseWideNavigation(
                items = visibleNavItems,
                currentRoute = currentRoute,
                onNavigate = navigateTo
            )
            Scaffold(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    if (currentSong != null) {
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.background)
                                .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.xs)
                        ) {
                            miniPlayer()
                        }
                    }
                },
                content = navContent
            )
        }
    }
}

@Composable
private fun MuseMiniPlayerChrome(
    currentSong: Song?,
    isPlaying: Boolean,
    shuffleMode: Boolean,
    progressProvider: () -> Float,
    reduceMotion: Boolean,
    onTogglePlayPause: () -> Unit,
    onOpenPlayer: () -> Unit,
    onOpenQueue: () -> Unit
) {
    AnimatedVisibility(
        visible = currentSong != null,
        enter = if (reduceMotion) EnterTransition.None else MotionMiniPlayer.slideIn + MotionMiniPlayer.fadeIn,
        exit = if (reduceMotion) ExitTransition.None else MotionMiniPlayer.slideOut + MotionMiniPlayer.fadeOut
    ) {
        currentSong?.let { song ->
            MiniPlayer(
                song = song,
                isPlaying = isPlaying,
                progressProvider = progressProvider,
                shuffleMode = shuffleMode,
                onTogglePlayPause = onTogglePlayPause,
                onClick = onOpenPlayer,
                onQueueClick = onOpenQueue,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun MuseCompactDock(
    items: List<NavItem>,
    currentRoute: String?,
    onNavigate: (Screen) -> Unit,
    miniPlayer: @Composable () -> Unit
) {
    val visuals = LocalMuseVisualStyle.current
    Column(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xxs)
    ) {
        Box(modifier = Modifier.padding(bottom = AppSpacing.xxs)) {
            miniPlayer()
        }
        Surface(
            shape = MuseShapeTokens.Pill,
            color = visuals.floatingSurface,
            shadowElevation = visuals.floatingElevation
        ) {
            NavigationBar(
                modifier = Modifier.height(68.dp),
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                tonalElevation = 0.dp
            ) {
                items.forEach { item ->
                    MuseNavigationBarItem(
                        item = item,
                        selected = currentRoute == item.screen.route,
                        onClick = { onNavigate(item.screen) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.MuseNavigationBarItem(item: NavItem, selected: Boolean, onClick: () -> Unit) {
    val reduceMotion = LocalReduceMotion.current
    val label = stringResource(item.labelRes)
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.1f else 1f,
        animationSpec = if (reduceMotion) snap() else MotionNav.iconSelect,
        label = "nav_icon_scale"
    )
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = {
            Icon(
                imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                contentDescription = label,
                modifier = Modifier
                    .size(MuseDimens.IconSizeMedium)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    }
            )
        },
        label = {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
        },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f),
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f),
            indicatorColor = MaterialTheme.colorScheme.primaryContainer
        )
    )
}

@Composable
private fun MuseWideNavigation(
    items: List<NavItem>,
    currentRoute: String?,
    onNavigate: (Screen) -> Unit
) {
    val visuals = LocalMuseVisualStyle.current
    Surface(
        modifier = Modifier.width(92.dp).fillMaxHeight(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(1.dp, visuals.cardBorder)
    ) {
        NavigationRail(
            modifier = Modifier.fillMaxHeight(),
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            header = {
                Surface(
                    modifier = Modifier.padding(vertical = AppSpacing.lg).size(48.dp),
                    shape = MuseShapeTokens.Card,
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "M",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        ) {
            items.forEach { item ->
                val label = stringResource(item.labelRes)
                val selected = currentRoute == item.screen.route
                NavigationRailItem(
                    selected = selected,
                    onClick = { onNavigate(item.screen) },
                    icon = {
                        Icon(
                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                            contentDescription = label
                        )
                    },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                    colors = NavigationRailItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}
