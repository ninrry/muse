package luzzr.muse.ui

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import luzzr.muse.MainViewModel
import luzzr.muse.R
import luzzr.muse.ui.animation.MotionMiniPlayer
import luzzr.muse.ui.animation.MotionNav
import luzzr.muse.ui.components.MiniPlayer
import luzzr.muse.ui.components.LocalReduceMotion
import luzzr.muse.ui.navigation.Screen
import luzzr.muse.ui.theme.AppSpacing
import luzzr.muse.ui.theme.MuseDimens

data class NavItem(
    val screen: Screen,
    @StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val navItems = listOf(
    NavItem(Screen.Home, R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home),
    NavItem(Screen.Library, R.string.nav_library, Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic),
    NavItem(Screen.Audiobook, R.string.nav_audiobook, Icons.AutoMirrored.Filled.MenuBook, Icons.AutoMirrored.Outlined.MenuBook),
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
    // progress 不在 Scaffold 收集：由 MiniPlayer 叶子节点 per-frame 读取，避免 20Hz 整栏重组
    val progressProvider = remember(viewModel) { { viewModel.progressRatio() } }
    val shuffleMode by viewModel.shuffleMode.collectAsStateWithLifecycle()
    val isAudiobookVisible by viewModel.isAudiobookVisible.collectAsStateWithLifecycle()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val isPlayerScreen = Screen.Player.isPlayerRoute(currentDestination?.route)
    val isReadAlongScreen = Screen.ReadAlong.isReadAlongRoute(currentDestination?.route)
    val reduceMotion = LocalReduceMotion.current
    val visibleNavItems = remember(isAudiobookVisible) {
        navItems.filter { it.screen != Screen.Audiobook || isAudiobookVisible }
    }

    LaunchedEffect(isAudiobookVisible, currentDestination?.route) {
        if (!isAudiobookVisible && currentDestination?.route == Screen.Audiobook.route) {
            navController.navigate(Screen.Settings.route) {
                popUpTo(Screen.Audiobook.route) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (isPlayerScreen || isReadAlongScreen) return@Scaffold
            Column {
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
                            onTogglePlayPause = { viewModel.togglePlayPause() },
                            onClick = {
                                navController.navigate(Screen.Player.route) {
                                    launchSingleTop = true
                                }
                            },
                            onQueueClick = {
                                navController.navigate(Screen.Player.QUEUE_ROUTE) {
                                    launchSingleTop = true
                                }
                            },
                            modifier = Modifier.padding(
                                start = AppSpacing.sm,
                                end = AppSpacing.sm,
                                top = AppSpacing.xxs,
                                bottom = AppSpacing.xxs
                            )
                        )
                    }
                }
                NavigationBar(
                    modifier = Modifier.height(MuseDimens.NavigationBarHeight),
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 0.dp,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    visibleNavItems.forEach { item ->
                        val label = stringResource(item.labelRes)
                        val selected = currentDestination?.route == item.screen.route
                        val iconScale by animateFloatAsState(
                            targetValue = if (selected) 1.08f else 1f,
                            animationSpec = if (reduceMotion) snap() else MotionNav.iconSelect,
                            label = "nav_icon_scale"
                        )
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
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
                                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
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
}
