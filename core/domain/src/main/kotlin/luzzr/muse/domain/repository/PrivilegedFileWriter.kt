package luzzr.muse.domain.repository

import luzzr.muse.core.result.OperationResult

/**
 * Abstraction for writing files with elevated privileges (e.g. via Shizuku).
 *
 * Implementations are provided by the app module, since they require Android
 * framework or third-party APIs that should not leak into core:data.
 */
interface PrivilegedFileWriter {

    /** Whether the privileged writer is currently available and authorized. */
    fun isAvailable(): Boolean

    /**
     * Copy a local file to [targetPath] using elevated privileges.
     *
     * @param source the file to copy
     * @param targetPath absolute path of the destination file
     */
    suspend fun copyToTarget(source: java.io.File, targetPath: String): OperationResult<Unit>
}
