package luzzr.muse.domain.model

data class CoverGenState(
    val isRunning: Boolean = false,
    val processed: Int = 0,
    val total: Int = 0,
    val errors: Int = 0
)
