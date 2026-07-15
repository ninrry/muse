package luzzr.muse.ui.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Library : Screen("library")
    data object Audiobook : Screen("audiobook")
    data object Player : Screen("player") {
        const val QUEUE_ROUTE = "player/queue"
        fun isPlayerRoute(route: String?): Boolean =
            route == this.route || route == QUEUE_ROUTE
    }
    data object Settings : Screen("settings")
    data object PlaylistDetail : Screen("playlist/{playlistId}") {
        fun createRoute(playlistId: Long) = "playlist/$playlistId"
    }
}

val bottomNavScreens = listOf(Screen.Home, Screen.Library, Screen.Audiobook, Screen.Settings)
