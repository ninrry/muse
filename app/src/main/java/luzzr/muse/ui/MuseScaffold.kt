package luzzr.muse.ui

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import luzzr.muse.MainViewModel
import luzzr.muse.R
import luzzr.muse.ui.animation.MotionMiniPlayer
import luzzr.muse.ui.animation.MotionNav
import luzzr.muse.ui.components.LocalReduceMotion
import luzzr.muse.ui.components.MiniPlayer
import luzzr.muse.ui.navigation.Screen
import luzzr.muse.ui.theme.MuseDimens
import luzzr.muse.ui.theme.MuseIcons
import luzzr.muse.ui.theme.MuseShapeTokens

data class NavItem(
    val screen: Screen,
    @StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val navItems = listOf(
    NavItem(Screen.Home, R.string.nav_home, MuseIcons.Home, MuseIcons.HomeOutlined),
    NavItem(Screen.Library, R.string.nav_library, MuseIcons.Library, MuseIcons.LibraryOutlined),
    NavItem(Screen.Audiobook, R.string.nav_audiobook, MuseIcons.Audiobook, MuseIcons.AudiobookOutlined),
    NavItem(Screen.Settings, R.string.nav_settings, MuseIcons.Settings, MuseIcons.SettingsOutlined)
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 主界面内容贯通延伸至底层
        MuseNavHost(
            navController = navController,
            innerPadding = PaddingValues(bottom = if (isPlayerScreen || isReadAlongScreen) 0.dp else 140.dp),
            hasAudioPermission = hasAudioPermission,
            hasNotificationPermission = hasNotificationPermission,
            onRequestPermission = onRequestPermission,
            onRequestNotificationPermission = onRequestNotificationPermission,
            showAudiobook = isAudiobookVisible
        )

        // 真正透光纯图标悬浮式底部导航胶囊 (True Floating Icon-Only Navigation Bar)
        if (!isPlayerScreen && !isReadAlongScreen) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 28.dp, end = 28.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
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
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }

                // 悬浮透光半透明纯图标胶囊卡片容器
                Surface(
                    shape = MuseShapeTokens.Pill,
                    color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.88f),
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    ),
                    shadowElevation = 0.dp,
                    tonalElevation = 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    NavigationBar(
                        modifier = Modifier.height(56.dp),
                        containerColor = Color.Transparent,
                        tonalElevation = 0.dp,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ) {
                        visibleNavItems.forEach { item ->
                            val label = stringResource(item.labelRes)
                            val selected = currentDestination?.route == item.screen.route
                            val iconScale by animateFloatAsState(
                                targetValue = if (selected) 1.12f else 1f,
                                animationSpec = if (reduceMotion) snap() else MotionNav.iconSelect,
                                label = "nav_icon_scale"
                            )
                            NavigationBarItem(
                                selected = selected,
                                alwaysShowLabel = false,
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
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
