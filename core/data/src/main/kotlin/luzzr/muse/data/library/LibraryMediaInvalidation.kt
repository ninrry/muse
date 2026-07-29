package luzzr.muse.data.library

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Signals that a read-along import changed the ownership boundary of local media.
 * The music repository reloads its persisted library and drops any source copies
 * that now belong to a synchronized-reading package.
 */
@Singleton
class LibraryMediaInvalidation @Inject constructor() {
    private val _requests = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 1)
    val requests = _requests.asSharedFlow()

    fun requestReload() {
        _requests.tryEmit(Unit)
    }
}
