package com.example.synced.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.util.concurrent.CancellationException

class ErrorMapperTest {

    @Test
    fun `IOException maps to offline`() {
        assertEquals(AppError.Offline, ErrorMapper.map(IOException("connection reset")))
    }

    @Test
    fun `socket timeout maps to timeout`() {
        assertEquals(AppError.Timeout, ErrorMapper.map(SocketTimeoutException("timed out")))
    }

    @Test
    fun `generic interrupted io maps to timeout`() {
        assertEquals(AppError.Timeout, ErrorMapper.map(InterruptedIOException("timeout")))
    }

    @Test
    fun `ApiException maps to typed HTTP error with readable message`() {
        val error = ErrorMapper.map(ApiException(500, "HTTP 500"))
        assertTrue(error is AppError.Http)
        assertEquals(500, (error as AppError.Http).code)
        assertEquals("The server is having trouble. Try again later.", error.userMessage)
    }

    @Test
    fun `retrofit HttpException maps to typed HTTP error`() {
        val httpException = HttpException(Response.error<Any>(404, "".toResponseBody()))
        val error = ErrorMapper.map(httpException)
        assertTrue(error is AppError.Http)
        assertEquals(404, (error as AppError.Http).code)
    }

    @Test
    fun `unknown exceptions map to generic message with detail`() {
        val error = ErrorMapper.map(IllegalStateException("boom"))
        assertTrue(error is AppError.Unknown)
        assertTrue(error.userMessage.contains("boom"))
    }

    @Test
    fun `cancellation is rethrown, never mapped`() {
        assertThrows(CancellationException::class.java) {
            ErrorMapper.map(CancellationException("cancelled"))
        }
    }
}
