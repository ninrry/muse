package luzzr.muse.ui.screens.audiobook

internal enum class ReadAlongFollowEvent {
    UserInteraction,
    Resume,
    ExplicitJump
}

internal data class ReadAlongFollowState(
    val suspended: Boolean = false,
    val resumeRequest: Int = 0
)

internal fun ReadAlongFollowState.onEvent(event: ReadAlongFollowEvent): ReadAlongFollowState = when (event) {
    ReadAlongFollowEvent.UserInteraction -> copy(suspended = true)
    ReadAlongFollowEvent.Resume,
    ReadAlongFollowEvent.ExplicitJump -> copy(
        suspended = false,
        resumeRequest = resumeRequest + 1
    )
}
