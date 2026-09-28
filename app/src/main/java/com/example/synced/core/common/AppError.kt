package com.example.synced.core.common

/**
 * Typed error model with a user-readable message already attached —
 * screens never format exceptions themselves.
 */
sealed class AppError(val userMessage: String) {

    /** No usable network (device offline, host unreachable). */
    data object Offline : AppError("No internet connection. Showing your saved content.")

    /** Connectivity existed but the request timed out. */
    data object Timeout : AppError("The request timed out. Try again.")

    /** Server answered with a non-2xx status. */
    data class Http(val code: Int, val serverMessage: String? = null) :
        AppError(serverMessage ?: defaultHttpMessage(code))

    /** Anything else that went wrong on the transport/parsing side. */
    data class Unknown(val detail: String? = null) :
        AppError(detail?.let { "Something went wrong: $it" } ?: "Something went wrong. Please try again.")

    companion object {
        fun defaultHttpMessage(code: Int): String = when {
            code == 404 -> "Couldn't find what you're looking for."
            code in 400..499 -> "Request failed (HTTP $code)."
            code >= 500 -> "The server is having trouble. Try again later."
            else -> "Request failed (HTTP $code)."
        }
    }
}
