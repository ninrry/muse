package luzzr.muse.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import luzzr.muse.ui.animation.MotionDuration
import luzzr.muse.ui.animation.MotionEasing
import luzzr.muse.ui.components.LocalReduceMotion
import luzzr.muse.ui.navigation.Screen
import luzzr.muse.ui.screens.home.HomeRoute
import luzzr.muse.ui.screens.home.PlaylistDetailRoute
import luzzr.muse.ui.screens.library.LibraryScreen
import luzzr.muse.ui.screens.player.PlayerRoute
import luzzr.muse.ui.screens.settings.SettingsRoute

@Composable
fun MuseNavHost(
    navController: NavHostController,
    innerPadding: PaddingValues,
    hasAudioPermission: Boolean,
    onRequestPermission: () -> Unit
) {
    // 底部导航Tab顺序: Home(0) -> Library(1) -> Audiobook(2) -> Settings(3)
    // 从左边Tab到右边Tab：新页面从右边滑入
    // 从右边Tab回左边Tab：新页面从左边滑入
    val tabOrder = mapOf(
        Screen.Home.route to 0,
        Screen.Library.route to 1,
        Screen.Audiobook.route to 2,
        Screen.Settings.route to 3
    )
    val reduceMotion = LocalReduceMotion.current

    // Tab切换的进入动画
    val tabEnterTransition: androidx.compose.animation.AnimatedContentTransitionScope<androidx.navigation.NavBackStackEntry>.() -> androidx.compose.animation.EnterTransition = {
        if (reduceMotion) {
            EnterTransition.None
        } else {
            val fromRoute = initialState.destination.route
            val toRoute = targetState.destination.route
            val fromIndex = fromRoute?.let { tabOrder[it] } ?: 0
            val toIndex = toRoute?.let { tabOrder[it] } ?: 0

            if (toIndex > fromIndex) {
                // 去往右侧页面：从右向左滑入
                slideInHorizontally(
                    initialOffsetX = { it / 4 },
                    animationSpec = tween(durationMillis = MotionDuration.long2, easing = MotionEasing.standard)
                ) + fadeIn(animationSpec = tween(durationMillis = MotionDuration.long2))
            } else {
                // 去往左侧页面：从左向右滑入
                slideInHorizontally(
                    initialOffsetX = { -it / 4 },
                    animationSpec = tween(durationMillis = MotionDuration.long2, easing = MotionEasing.standard)
                ) + fadeIn(animationSpec = tween(durationMillis = MotionDuration.long2))
            }
        }
    }

    // Tab切换的退出动画
    val tabExitTransition: androidx.compose.animation.AnimatedContentTransitionScope<androidx.navigation.NavBackStackEntry>.() -> androidx.compose.animation.ExitTransition = {
        if (reduceMotion) {
            ExitTransition.None
        } else {
            val fromRoute = initialState.destination.route
            val toRoute = targetState.destination.route
            val fromIndex = fromRoute?.let { tabOrder[it] } ?: 0
            val toIndex = toRoute?.let { tabOrder[it] } ?: 0

            if (toIndex > fromIndex) {
                // 去往右侧页面：当前页面向左滑出
                slideOutHorizontally(
                    targetOffsetX = { -it / 4 },
                    animationSpec = tween(durationMillis = MotionDuration.medium1, easing = MotionEasing.accelerate)
                ) + fadeOut(animationSpec = tween(durationMillis = MotionDuration.medium1))
            } else {
                // 去往左侧页面：当前页面向右滑出
                slideOutHorizontally(
                    targetOffsetX = { it / 4 },
                    animationSpec = tween(durationMillis = MotionDuration.medium1, easing = MotionEasing.accelerate)
                ) + fadeOut(animationSpec = tween(durationMillis = MotionDuration.medium1))
            }
        }
    }

    val playerEnter = if (reduceMotion) {
        EnterTransition.None
    } else {
        slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(durationMillis = 400, easing = MotionEasing.emphasizedDecelerate)
        ) + fadeIn(animationSpec = tween(durationMillis = 400))
    }
    val playerExit = if (reduceMotion) {
        ExitTransition.None
    } else {
        slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(durationMillis = 350, easing = MotionEasing.emphasizedAccelerate)
        ) + fadeOut(animationSpec = tween(durationMillis = 350))
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = Modifier.fillMaxSize(),
        enterTransition = tabEnterTransition,
        exitTransition = tabExitTransition
    ) {
        composable(Screen.Home.route) {
            HomeRoute(
                innerPadding = innerPadding,
                hasPermission = hasAudioPermission,
                onRequestPermission = onRequestPermission,
                onNavigateToPlaylist = { playlistId ->
                    navController.navigate(Screen.PlaylistDetail.createRoute(playlistId))
                }
            )
        }

        composable(
            route = Screen.PlaylistDetail.route,
            arguments = listOf(
                navArgument("playlistId") { type = NavType.LongType }
            ),
            enterTransition = {
                if (reduceMotion) {
                    EnterTransition.None
                } else {
                    slideInHorizontally(
                        initialOffsetX = { it / 3 },
                        animationSpec = tween(
                            durationMillis = MotionDuration.long2,
                            easing = MotionEasing.emphasizedDecelerate
                        )
                    ) + fadeIn(animationSpec = tween(MotionDuration.long2))
                }
            },
            exitTransition = {
                if (reduceMotion) {
                    ExitTransition.None
                } else {
                    slideOutHorizontally(
                        targetOffsetX = { -it / 5 },
                        animationSpec = tween(
                            durationMillis = MotionDuration.medium1,
                            easing = MotionEasing.accelerate
                        )
                    ) + fadeOut(animationSpec = tween(MotionDuration.medium1))
                }
            },
            popEnterTransition = {
                if (reduceMotion) {
                    EnterTransition.None
                } else {
                    slideInHorizontally(
                        initialOffsetX = { -it / 5 },
                        animationSpec = tween(
                            durationMillis = MotionDuration.long2,
                            easing = MotionEasing.emphasizedDecelerate
                        )
                    ) + fadeIn(animationSpec = tween(MotionDuration.long2))
                }
            },
            popExitTransition = {
                if (reduceMotion) {
                    ExitTransition.None
                } else {
                    slideOutHorizontally(
                        targetOffsetX = { it / 3 },
                        animationSpec = tween(
                            durationMillis = MotionDuration.medium1,
                            easing = MotionEasing.accelerate
                        )
                    ) + fadeOut(animationSpec = tween(MotionDuration.medium1))
                }
            }
        ) { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getLong("playlistId") ?: return@composable
            PlaylistDetailRoute(
                playlistId = playlistId,
                innerPadding = innerPadding,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Library.route) {
            LibraryScreen(innerPadding = innerPadding)
        }
        composable(Screen.Audiobook.route) {
            luzzr.muse.ui.screens.audiobook.AudiobookRoute(innerPadding = innerPadding)
        }

        composable(
            route = Screen.Player.route,
            enterTransition = { playerEnter },
            exitTransition = { playerExit }
        ) {
            PlayerRoute(
                innerPadding = PaddingValues(),
                onBack = { navController.popBackStack() },
                openQueueOnStart = false
            )
        }
        composable(
            route = Screen.Player.QUEUE_ROUTE,
            enterTransition = { playerEnter },
            exitTransition = { playerExit }
        ) {
            PlayerRoute(
                innerPadding = PaddingValues(),
                onBack = { navController.popBackStack() },
                openQueueOnStart = true
            )
        }
        composable(Screen.Settings.route) {
            SettingsRoute(
                innerPadding = innerPadding,
                hasAudioPermission = hasAudioPermission,
                onRequestAudioPermission = onRequestPermission
            )
        }
    }
}
