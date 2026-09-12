package com.vernai.core.common.result

/**
 * Standard Result monad for VernAI domain and data operations.
 * Explicitly distinguishes loading, success, and structured domain failures.
 */
sealed interface VernAiResult<out T> {
    data class Success<T>(val data: T) : VernAiResult<T>
    data class Error(val exception: Throwable, val message: String? = exception.message) : VernAiResult<Nothing>
    data class Loading(val progress: Float? = null, val stage: String? = null) : VernAiResult<Nothing>
}

inline fun <T, R> VernAiResult<T>.map(transform: (T) -> R): VernAiResult<R> {
    return when (this) {
        is VernAiResult.Success -> VernAiResult.Success(transform(data))
        is VernAiResult.Error -> this
        is VernAiResult.Loading -> this
    }
}

inline fun <T> VernAiResult<T>.onSuccess(action: (T) -> Unit): VernAiResult<T> {
    if (this is VernAiResult.Success) action(data)
    return this
}

inline fun <T> VernAiResult<T>.onError(action: (Throwable, String?) -> Unit): VernAiResult<T> {
    if (this is VernAiResult.Error) action(exception, message)
    return this
}
