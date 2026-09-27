package com.revyu.app.core.util

sealed class RevyuResult<out T> {
    data class Success<T>(val data: T) : RevyuResult<T>()
    data class Error(val message: String, val isRateLimit: Boolean = false, val isMissingApiKey: Boolean = false, val cause: Throwable? = null) : RevyuResult<Nothing>()
}

inline fun <T> RevyuResult<T>.onSuccess(action: (T) -> Unit): RevyuResult<T> {
    if (this is RevyuResult.Success) action(data)
    return this
}

inline fun <T> RevyuResult<T>.onError(action: (RevyuResult.Error) -> Unit): RevyuResult<T> {
    if (this is RevyuResult.Error) action(this)
    return this
}
