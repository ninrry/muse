package luzzr.muse.data.error

/**
 * Sealed hierarchy of every failure the UI layer is expected to surface.
 * Repositories should throw these (or subclasses) instead of letting raw
 * `Exception` bubble up; the UI layer then catches the most specific type
 * and shows a localised message.
 */
sealed class AppException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

class NetworkException(
    message: String,
    cause: Throwable? = null
) : AppException(message, cause)

class DatabaseException(
    message: String,
    cause: Throwable? = null
) : AppException(message, cause)

class FileOperationException(
    message: String,
    cause: Throwable? = null
) : AppException(message, cause)

class ParsingException(
    message: String,
    cause: Throwable? = null
) : AppException(message, cause)

class MetadataException(
    message: String,
    cause: Throwable? = null
) : AppException(message, cause)

class PermissionDeniedException(
    message: String,
    cause: Throwable? = null
) : AppException(message, cause)

class NotFoundException(
    message: String,
    cause: Throwable? = null
) : AppException(message, cause)

class CancelledException(
    message: String = "Operation was cancelled",
    cause: Throwable? = null
) : AppException(message, cause)

/**
 * Maps arbitrary throwables to the closest [AppException] subtype so that
 * `catch (e: Exception)` blocks in repositories produce meaningful errors
 * for the UI layer.
 */
object ErrorMapper {
    fun toAppException(throwable: Throwable): AppException = when (throwable) {
        is AppException -> throwable
        is SecurityException -> PermissionDeniedException(throwable.message ?: "Permission denied", throwable)
        is java.io.FileNotFoundException -> NotFoundException(throwable.message ?: "Not found", throwable)
        is java.net.SocketTimeoutException -> NetworkException("Network timed out", throwable)
        is java.net.UnknownHostException -> NetworkException("Host unreachable", throwable)
        is java.io.IOException -> FileOperationException(throwable.message ?: "IO error", throwable)
        is org.json.JSONException -> ParsingException(throwable.message ?: "Parse error", throwable)
        is IllegalStateException -> DatabaseException(throwable.message ?: "Illegal state", throwable)
        else -> DatabaseException(throwable.message ?: "Unknown error", throwable)
    }
}
