package luzzr.muse.domain.artwork

import luzzr.muse.core.result.OperationResult
import luzzr.muse.domain.model.CoverGenState
import kotlinx.coroutines.flow.StateFlow

interface DefaultCoverGenerationController {
    val coverGenState: StateFlow<CoverGenState>

    suspend fun generateAll(): OperationResult<Unit>
}
