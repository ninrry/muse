package luzzr.muse.core.result

sealed interface OperationResult<out T> {
    data class Success<T>(val value: T) : OperationResult<T>

    data class Failure(
        val error: OperationError,
        val message: String? = null
    ) : OperationResult<Nothing>
}

enum class OperationError {
    PERMISSION_DENIED,
    UNSUPPORTED_FILE,
    NETWORK,
    IO,
    NOT_FOUND,
    DATABASE,
    CONFLICT,
    UNKNOWN
}

val OperationResult<*>.isSuccess: Boolean
    get() = this is OperationResult.Success
