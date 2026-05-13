package luzzr.muse.ui.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Library : Screen("library")
    data object Player : Screen("player")
    data object Settings : Screen("settings")
}

val bottomNavScreens = listOf(Screen.Home, Screen.Library, Screen.Player, Screen.Settings)
