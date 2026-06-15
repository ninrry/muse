package luzzr.muse.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import luzzr.muse.ui.animation.MotionEasing
import luzzr.muse.ui.animation.MotionPageTransition
import luzzr.muse.ui.navigation.Screen
import luzzr.muse.ui.screens.home.HomeRoute
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
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = Modifier.fillMaxSize(),
        enterTransition = { MotionPageTransition.fadeThrough },
        exitTransition = { MotionPageTransition.fadeThroughExit }
    ) {
        composable(Screen.Home.route) {
            HomeRoute(
                innerPadding = innerPadding,
                hasPermission = hasAudioPermission,
                onRequestPermission = onRequestPermission
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
            enterTransition = {
                slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(durationMillis = 400, easing = MotionEasing.emphasizedDecelerate)
                ) + fadeIn(animationSpec = tween(durationMillis = 400))
            },
            exitTransition = {
                slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(durationMillis = 350, easing = MotionEasing.emphasizedAccelerate)
                ) + fadeOut(animationSpec = tween(durationMillis = 350))
            }
        ) {
            PlayerRoute(innerPadding = innerPadding, onBack = { navController.popBackStack() })
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
