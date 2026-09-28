package com.example.synced.core.network

import com.example.synced.core.common.ApiException
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Centralized HTTP error handling: converts any non-2xx response into a typed
 * [ApiException] at the transport layer, so every Retrofit endpoint fails the same
 * way and repositories only deal with one exception type for HTTP errors.
 */
class ErrorInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (!response.isSuccessful) {
            val code = response.code
            response.close() // release the connection before throwing
            throw ApiException(code, "HTTP $code")
        }
        return response
    }
}
