package com.epubaudioreader.core.common.result

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val exception: Throwable, val message: String = exception.message ?: "Unknown error") : Result<Nothing>()
}

inline fun <T, R> Result<T>.map(transform: (T) -> R): Result<R> =
    when (this) {
        is Result.Success -> Result.Success(transform(data))
        is Result.Error -> this
    }

inline fun <T> Result<T>.onSuccess(action: (T) -> Unit): Result<T> {
    if (this is Result.Success) action(data)
    return this
}

inline fun <T> Result<T>.onError(action: (Result.Error) -> Unit): Result<T> {
    if (this is Result.Error) action(this)
    return this
}

inline fun <T, R> Result<T>.fold(onSuccess: (T) -> R, onError: (Result.Error) -> R): R =
    when (this) {
        is Result.Success -> onSuccess(data)
        is Result.Error -> onError(this)
    }
