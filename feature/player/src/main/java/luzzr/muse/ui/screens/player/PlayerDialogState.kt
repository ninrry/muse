package luzzr.muse.ui.screens.player

sealed class PlayerDialogState {
    object None : PlayerDialogState()
    object Queue : PlayerDialogState()
    object SleepTimer : PlayerDialogState()
}
