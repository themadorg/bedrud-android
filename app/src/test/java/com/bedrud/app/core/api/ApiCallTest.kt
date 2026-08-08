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
    fun `apiBody reports the fallback on a non-2xx response with no error text`() = runBlocking {
        var reported: String? = null
        val body = apiBody("fallback", { reported = it }) {
            Response.error<String>(500, "".toResponseBody())
        }
        assertNull(body)
        assertEquals("fallback", reported)
    }

    @Test
    fun `apiBody reports the server's error text on a non-2xx response`() = runBlocking {
        var reported: String? = null
        val body = apiBody("fallback", { reported = it }) {
            Response.error<String>(
                403,
                """{"error":"Not available for guest accounts"}""".toResponseBody()
            )
        }
        assertNull(body)
        assertEquals("Not available for guest accounts", reported)
    }

    @Test
    fun `apiBody prefers the server's message over its error`() = runBlocking {
        var reported: String? = null
        apiBody("fallback", { reported = it }) {
            Response.error<String>(
                400,
                """{"error":"bad_request","message":"Room name is already taken"}""".toResponseBody()
            )
        }
        assertEquals("Room name is already taken", reported)
    }

    @Test
    fun `apiBody falls back when the error body is not parseable`() = runBlocking {
        var reported: String? = null
        apiBody("fallback", { reported = it }) {
            Response.error<String>(502, "<html>Bad Gateway</html>".toResponseBody())
        }
        assertEquals("fallback", reported)

        // Well-formed JSON, but nothing worth showing the user.
        apiBody("fallback", { reported = it }) {
            Response.error<String>(500, """{"error":""}""".toResponseBody())
        }
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

    @Test
    fun `apiAction reports the server's error text on a non-2xx response`() = runBlocking {
        var reported: String? = null
        assertFalse(apiAction("fallback", { reported = it }) {
            Response.error<Unit>(403, """{"error":"Only the room owner can delete it"}""".toResponseBody())
        })
        assertEquals("Only the room owner can delete it", reported)
    }
}
