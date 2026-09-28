package com.example.synced.core.common

import retrofit2.HttpException
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeoutException

/**
 * Central exception → user-message mapping. Every repository catches Throwables and
 * runs them through here so screens only ever see [AppError].
 */
object ErrorMapper {

    fun map(throwable: Throwable): AppError = when (throwable) {
        is CancellationException -> throw throwable // never swallow coroutine cancellation
        // Transport-level HTTP failures: use the code-based readable message (we don't
        // surface raw error bodies to users).
        is ApiException -> AppError.Http(throwable.code)
        is HttpException -> AppError.Http(throwable.code())
        is InterruptedIOException, is TimeoutException -> AppError.Timeout
        // IOException covers connect failures, unknown hosts, connection resets —
        // all mean "we couldn't reach the server", which reads as offline to the user.
        is IOException -> AppError.Offline
        else -> AppError.Unknown(throwable.message)
    }
}
