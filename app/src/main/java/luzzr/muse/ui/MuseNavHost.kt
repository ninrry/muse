package luzzr.muse.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
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
        composable(Screen.Player.route) {
            PlayerRoute(innerPadding = innerPadding, onBack = { navController.popBackStack() })
        }
        composable(Screen.Settings.route) {
            SettingsRoute(innerPadding = innerPadding)
        }
    }
}
