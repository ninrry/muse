package luzzr.muse.ui

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import luzzr.muse.MainViewModel
import luzzr.muse.R
import luzzr.muse.ui.animation.MotionMiniPlayer
import luzzr.muse.ui.components.MiniPlayer
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
fun MuseScaffold(viewModel: MainViewModel, hasAudioPermission: Boolean, onRequestPermission: () -> Unit) {
    val navController = rememberNavController()
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val duration by viewModel.duration.collectAsStateWithLifecycle()
    val shuffleMode by viewModel.shuffleMode.collectAsStateWithLifecycle()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val isPlayerScreen = Screen.Player.isPlayerRoute(currentDestination?.route)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (isPlayerScreen) return@Scaffold
            Column {
                Spacer(Modifier.height(AppSpacing.xxs))
                AnimatedVisibility(
                    visible = currentSong != null,
                    enter = MotionMiniPlayer.slideIn + MotionMiniPlayer.fadeIn,
                    exit = MotionMiniPlayer.slideOut + MotionMiniPlayer.fadeOut
                ) {
                    currentSong?.let { song ->
                        Column {
                            MiniPlayer(
                                song = song,
                                isPlaying = isPlaying,
                                progress = if (duration > 0) progress.toFloat() / duration else 0f,
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
                                modifier = Modifier.padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xxxs)
                            )
                            Spacer(Modifier.height(AppSpacing.sm))
                        }
                    }
                }
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = AppSpacing.xxxs
                ) {
                    navItems.forEach { item ->
                        val label = stringResource(item.labelRes)
                        val selected = currentDestination?.route == item.screen.route
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
                                    modifier = Modifier.size(MuseDimens.IconSizeNormal)
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
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        MuseNavHost(navController, innerPadding, hasAudioPermission, onRequestPermission)
    }
}
