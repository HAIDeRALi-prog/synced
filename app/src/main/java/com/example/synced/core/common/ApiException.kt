package com.example.synced.core.common

/**
 * Thrown by [com.example.synced.core.network.ErrorInterceptor] for any non-2xx response,
 * so HTTP failures reach the repository as a single typed exception regardless of endpoint.
 * Lives in core/common so the error model doesn't depend on the network package.
 */
class ApiException(val code: Int, message: String) : RuntimeException(message)
