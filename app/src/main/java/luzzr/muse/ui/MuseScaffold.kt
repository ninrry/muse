package luzzr.muse.ui

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.PlayCircle
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import luzzr.muse.MainViewModel
import luzzr.muse.core.log.MuseLog
import luzzr.muse.ui.animation.MotionMiniPlayer
import luzzr.muse.ui.components.MiniPlayer
import luzzr.muse.ui.navigation.Screen
import luzzr.muse.ui.theme.AppSpacing
import luzzr.muse.ui.theme.MuseDimens

data class NavItem(val screen: Screen, val label: String, val selectedIcon: ImageVector, val unselectedIcon: ImageVector)

private val navItems = listOf(
    NavItem(Screen.Home, "首页", Icons.Filled.Home, Icons.Outlined.Home),
    NavItem(Screen.Library, "曲库", Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic),
    NavItem(Screen.Audiobook, "有声书", Icons.Filled.MenuBook, Icons.Outlined.MenuBook),
    NavItem(Screen.Player, "播放", Icons.Filled.PlayCircle, Icons.Outlined.PlayCircle),
    NavItem(Screen.Settings, "设置", Icons.Filled.Settings, Icons.Outlined.Settings)
)

@Composable
fun MuseScaffold(
    viewModel: MainViewModel,
    hasAudioPermission: Boolean,
    onRequestPermission: () -> Unit
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val duration by viewModel.duration.collectAsStateWithLifecycle()
    val shuffleMode by viewModel.shuffleMode.collectAsStateWithLifecycle()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            Column {
                Spacer(Modifier.height(AppSpacing.xxs))
                val isPlayerScreen = currentDestination?.route == Screen.Player.route
                AnimatedVisibility(
                    visible = !isPlayerScreen && currentSong != null,
                    enter = MotionMiniPlayer.slideIn + MotionMiniPlayer.fadeIn,
                    exit = MotionMiniPlayer.slideOut + MotionMiniPlayer.fadeOut
                ) {
                    currentSong?.let { song ->
                        Column {
                            MiniPlayer(
                                song = song, isPlaying = isPlaying,
                                progress = if (duration > 0) progress.toFloat() / duration else 0f, shuffleMode = shuffleMode,
                                onTogglePlayPause = {
                                    try {
                                        context.startForegroundService(
                                            Intent(context, luzzr.muse.player.MusicService::class.java)
                                        )
                                    } catch (e: Exception) {
                                        MuseLog.e("MainActivity", "Failed to start service", e)
                                    }
                                    viewModel.togglePlayPause()
                                },
                                onClick = {
                                    navController.navigate(Screen.Player.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true; restoreState = true
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
                        val selected = currentDestination?.route == item.screen.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true; restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.label,
                                    modifier = Modifier.size(MuseDimens.IconSizeNormal)
                                )
                            },
                            label = {
                                Text(item.label, style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
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
