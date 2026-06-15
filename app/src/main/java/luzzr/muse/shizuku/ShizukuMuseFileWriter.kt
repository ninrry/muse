package luzzr.muse.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import dagger.hilt.android.qualifiers.ApplicationContext
import luzzr.muse.BuildConfig
import luzzr.muse.core.log.MuseLog
import luzzr.muse.core.result.OperationError
import luzzr.muse.core.result.OperationResult
import luzzr.muse.domain.repository.PrivilegedFileWriter
import rikka.shizuku.Shizuku
import rikka.shizuku.Shizuku.UserServiceArgs
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

/**
 * [PrivilegedFileWriter] backed by Shizuku / Sui.
 *
 * The actual file operations run in a separate process as shell (adb) or root,
 * allowing writes to shared storage paths that are otherwise unreachable on
 * Android 11+ even with [android.Manifest.permission.MANAGE_EXTERNAL_STORAGE].
 */
@Singleton
class ShizukuMuseFileWriter @Inject constructor(
    @ApplicationContext private val context: Context
) : PrivilegedFileWriter {

    private var binder: IMuseFileService? = null
    private var isBinding = false

    override fun isAvailable(): Boolean {
        return try {
            Shizuku.pingBinder() &&
                Shizuku.getVersion() >= MIN_SHIZUKU_VERSION &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            MuseLog.w(TAG, "isAvailable: Shizuku check failed", e)
            false
        }
    }

    override suspend fun copyToTarget(source: File, targetPath: String): OperationResult<Unit> {
        if (!isAvailable()) {
            return OperationResult.Failure(
                OperationError.PERMISSION_DENIED,
                "Shizuku is not available or not authorized"
            )
        }

        val service = bindService() ?: return OperationResult.Failure(
            OperationError.UNKNOWN,
            "Failed to bind Shizuku file service"
        )

        return try {
            val success = withContext(Dispatchers.IO) {
                withTimeout(SERVICE_TIMEOUT_MS) {
                    service.copyFile(source.absolutePath, targetPath)
                }
            }
            if (success) {
                OperationResult.Success(Unit)
            } else {
                OperationResult.Failure(OperationError.IO, "Shizuku copyFile returned false")
            }
        } catch (e: Exception) {
            MuseLog.e(TAG, "copyToTarget: failed ${source.absolutePath} -> $targetPath", e)
            OperationResult.Failure(OperationError.IO, e.message)
        }
    }

    private suspend fun bindService(): IMuseFileService? {
        binder?.let { return it }

        if (isBinding) {
            return waitForBinder()
        }

        isBinding = true
        return try {
            val connected = suspendCancellableCoroutine { continuation ->
                val connection = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                        val fileService = service?.let { IMuseFileService.Stub.asInterface(it) }
                        binder = fileService
                        isBinding = false
                        continuation.resume(fileService)
                    }

                    override fun onServiceDisconnected(name: ComponentName?) {
                        binder = null
                    }
                }

                try {
                    val component = ComponentName(context.packageName, MuseFileService::class.java.name)
                    val args = UserServiceArgs(component)
                        .processNameSuffix("muse_file_service")
                        .debuggable(BuildConfig.DEBUG)
                        .version(SERVICE_VERSION)
                    Shizuku.bindUserService(args, connection)

                    continuation.invokeOnCancellation {
                        Shizuku.unbindUserService(args, connection, true)
                    }
                } catch (e: Exception) {
                    isBinding = false
                    MuseLog.e(TAG, "bindService: failed to bind user service", e)
                    continuation.resume(null)
                }
            }
            connected
        } catch (e: Exception) {
            isBinding = false
            MuseLog.e(TAG, "bindService: unexpected error", e)
            null
        }
    }

    private suspend fun waitForBinder(): IMuseFileService? {
        return withTimeoutOrNull(SERVICE_TIMEOUT_MS) {
            while (isBinding && binder == null) {
                kotlinx.coroutines.delay(BIND_POLL_MS)
            }
            binder
        }
    }

    private companion object {
        const val TAG = "ShizukuMuseFileWriter"
        const val MIN_SHIZUKU_VERSION = 11
        const val SERVICE_VERSION = 1
        const val SERVICE_TIMEOUT_MS = 10_000L
        const val BIND_POLL_MS = 100L
    }
}
