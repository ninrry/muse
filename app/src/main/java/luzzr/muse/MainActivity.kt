package luzzr.muse

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import luzzr.muse.ui.animation.MotionPageTransition
import luzzr.muse.ui.components.MiniPlayer
import luzzr.muse.ui.navigation.Screen
import luzzr.muse.ui.screens.home.HomeScreen
import luzzr.muse.ui.screens.library.LibraryScreen
import luzzr.muse.ui.screens.player.PlayerScreen
import luzzr.muse.ui.screens.settings.SettingsScreen
import luzzr.muse.ui.theme.MuseTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MuseMain()
        }
    }
}

data class NavItem(
    val screen: Screen,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

// Spec: 首页 Home, 曲库 LibraryMusic, 播放 PlayCircle, 设置 Settings
private val navItems = listOf(
    NavItem(Screen.Home, "首页", Icons.Filled.Home, Icons.Outlined.Home),
    NavItem(Screen.Library, "曲库", Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic),
    NavItem(Screen.Player, "播放", Icons.Filled.PlayCircle, Icons.Outlined.PlayCircle),
    NavItem(Screen.Settings, "设置", Icons.Filled.Settings, Icons.Outlined.Settings)
)

@Composable
fun MuseMain() {
    val context = LocalContext.current
    val museApp = context.applicationContext as MuseApp
    var hasAudioPermission by remember { mutableStateOf(checkAudioPermission(context)) }
    var hasNotificationPermission by remember { mutableStateOf(checkNotificationPermission(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasAudioPermission = permissions[Manifest.permission.READ_MEDIA_AUDIO] ?: true
        hasNotificationPermission = permissions[Manifest.permission.POST_NOTIFICATIONS] ?: true
    }

    LaunchedEffect(Unit) {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33) {
            if (!hasAudioPermission) needed.add(Manifest.permission.READ_MEDIA_AUDIO)
            if (!hasNotificationPermission) needed.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // Android 10-12: need READ_EXTERNAL_STORAGE
            if (!hasAudioPermission) needed.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val currentSong by museApp.playerState.currentSong.collectAsStateWithLifecycle()
    val isPlaying by museApp.playerState.isPlaying.collectAsStateWithLifecycle()
    val progress by museApp.playerState.progress.collectAsStateWithLifecycle()
    val duration by museApp.playerState.duration.collectAsStateWithLifecycle()
    val isDarkTheme by museApp.isDarkTheme.collectAsStateWithLifecycle()

    // Navigation bar / status bar colors per theme
    val navBarColor = if (isDarkTheme) Color(0xFF141210) else Color(0xFFFFFFFF)
    val statusBarColor = if (isDarkTheme) Color(0xFF141210) else Color(0xFFFDF8F3)

    // Apply system bar colors
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        val window = (context as? androidx.activity.ComponentActivity)?.window
        window?.let { w ->
            w.navigationBarColor = navBarColor.hashCode()
            w.statusBarColor = statusBarColor.hashCode()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                w.decorView.systemUiVisibility = if (!isDarkTheme) {
                    android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                } else {
                    w.decorView.systemUiVisibility and android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv() and android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
                }
            }
        }
    }

        MuseTheme(darkTheme = isDarkTheme) {
            Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            Column {
                // Top gap between content and MiniPlayer
                Spacer(Modifier.height(4.dp))

                // Mini Player: 滑入滑出 + 全屏播放器页面隐藏避免信息重复
                val isPlayerScreen = currentDestination?.route == Screen.Player.route
                AnimatedVisibility(
                    visible = !isPlayerScreen && currentSong != null,
                    enter = slideInVertically(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        initialOffsetY = { fullHeight -> fullHeight }
                    ) + fadeIn(animationSpec = tween(200, easing = FastOutSlowInEasing)),
                    exit = slideOutVertically(
                        animationSpec = tween(200),
                        targetOffsetY = { fullHeight -> fullHeight }
                    ) + fadeOut(animationSpec = tween(100))
                ) {
                    currentSong?.let { song ->
                        Column {
                            val progressFraction = if (duration > 0) progress.toFloat() / duration else 0f
                            MiniPlayer(
                                song = song,
                                isPlaying = isPlaying,
                                progress = progressFraction,
                                onTogglePlayPause = {
                                    // Start service if needed (survives process death on MIUI)
                                    try {
                                        context.startForegroundService(Intent(context, luzzr.muse.player.MusicService::class.java))
                                    } catch (_: Exception) {}
                                    museApp.playerState.togglePlayPause()
                                },
                                onClick = {
                                    navController.navigate(Screen.Player.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }

                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 2.dp
                ) {
                    navItems.forEach { item ->
                        val selected = currentDestination?.route == item.screen.route

                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.label,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            label = {
                                Text(
                                    item.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.fillMaxSize(),
            enterTransition = { MotionPageTransition.fadeThrough },
            exitTransition = { MotionPageTransition.fadeThroughExit }
        ) {
            composable(Screen.Home.route) {
                HomeScreen(innerPadding = innerPadding, hasPermission = hasAudioPermission)
            }
            composable(Screen.Library.route) {
                LibraryScreen(innerPadding = innerPadding)
            }
            composable(Screen.Player.route) {
                PlayerScreen(innerPadding = innerPadding)
            }
            composable(Screen.Settings.route) {
                SettingsScreen(innerPadding = innerPadding)
            }
        }
    }
}

}

private fun checkAudioPermission(context: android.content.Context): Boolean {
    return if (Build.VERSION.SDK_INT >= 33) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }
}

private fun checkNotificationPermission(context: android.content.Context): Boolean {
    return if (Build.VERSION.SDK_INT >= 33) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}
