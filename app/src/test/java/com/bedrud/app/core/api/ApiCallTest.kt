package com.bedrud.app.core.api

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.Response

class ApiCallTest {

    @Test
    fun `apiBody returns the body on success`() = runBlocking {
        val body = apiBody("fallback", { fail("unexpected error: $it") }) {
            Response.success("hello")
        }
        assertEquals("hello", body)
    }

    @Test
    fun `apiBody reports the fallback on a non-2xx response`() = runBlocking {
        var reported: String? = null
        val body = apiBody("fallback", { reported = it }) {
            Response.error<String>(500, "".toResponseBody())
        }
        assertNull(body)
        assertEquals("fallback", reported)
    }

    @Test
    fun `apiBody reports the fallback on a 2xx response with no body`() = runBlocking {
        var reported: String? = null
        val body = apiBody("fallback", { reported = it }) {
            Response.success<String>(null)
        }
        assertNull(body)
        assertEquals("fallback", reported)
    }

    @Test
    fun `apiBody reports the exception message, or the fallback when it has none`() = runBlocking {
        var reported: String? = null
        assertNull(apiBody<String>("fallback", { reported = it }) { throw IOException("boom") })
        assertEquals("boom", reported)

        assertNull(apiBody<String>("fallback", { reported = it }) { throw IOException() })
        assertEquals("fallback", reported)
    }

    @Test
    fun `apiBody rethrows cancellation without reporting it`() {
        var reported = false
        assertThrows(CancellationException::class.java) {
            runBlocking {
                apiBody<String>("fallback", { reported = true }) {
                    throw CancellationException("cancelled")
                }
            }
        }
        assertFalse(reported)
    }

    @Test
    fun `apiAction is true on success and false with the fallback on failure`() = runBlocking {
        var reported: String? = null
        assertTrue(apiAction("fallback", { fail("unexpected error: $it") }) { Response.success(Unit) })
        assertFalse(apiAction("fallback", { reported = it }) {
            Response.error<Unit>(400, "".toResponseBody())
        })
        assertEquals("fallback", reported)
    }
}
