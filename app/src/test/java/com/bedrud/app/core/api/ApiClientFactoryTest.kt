package com.bedrud.app.core.api

import com.bedrud.app.BuildConfig
import com.bedrud.app.core.auth.AuthManager
import com.bedrud.app.testutil.InMemorySharedPreferences
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.util.logging.Handler
import java.util.logging.LogRecord
import java.util.logging.Logger

/**
 * Covers what the session-carrying client writes to the log in a debug build: each call's headers,
 * but never the token it is signed with, and never a body, where passwords and tokens travel.
 */
class ApiClientFactoryTest : MockApiTest() {

    /** Every line OkHttp writes to the log during a test. */
    private val capturedLog = StringBuilder()

    private val captureHandler = object : Handler() {
        override fun publish(record: LogRecord) {
            capturedLog.appendLine(record.message)
        }

        override fun flush() = Unit

        override fun close() = Unit
    }

    /**
     * Where OkHttp's default logger writes off-device. Its handlers are attached here rather than
     * at the root because OkHttp stops this logger passing records up to its parents.
     */
    private val okHttpLogger = Logger.getLogger(OkHttpClient::class.java.name)

    @Before
    fun startCapture() {
        okHttpLogger.addHandler(captureHandler)
    }

    @After
    fun stopCapture() {
        okHttpLogger.removeHandler(captureHandler)
    }

    /** The client [ApiClientFactory] builds, signed in with [ACCESS_TOKEN]. */
    private fun sessionClient(): OkHttpClient {
        val baseURL = server.url("/api").toString()
        val authManager = AuthManager(InMemorySharedPreferences()).apply {
            saveTokens(ACCESS_TOKEN, REFRESH_TOKEN)
        }
        return ApiClientFactory(baseURL).createOkHttpClient(
            AuthInterceptor(authManager),
            TokenAuthenticator(authManager, baseURL),
        )
    }

    /** A response carrying [PROBE_HEADER], whose value in the log shows the log was captured at all. */
    private fun probeResponse(body: String = ""): MockResponse =
        MockResponse().setHeader(PROBE_HEADER, PROBE_HEADER_VALUE).setBody(body)

    @Test
    fun `should log a call's headers but never its access token`() {
        // A release build logs nothing at all, so there is nothing to look for in one.
        assumeTrue(BuildConfig.DEBUG)
        server.enqueue(probeResponse())

        sessionClient()
            .newCall(Request.Builder().url(server.url("/api/auth/me")).build())
            .execute()
            .close()

        assertTrue("nothing was captured from the log", capturedLog.contains(PROBE_HEADER_VALUE))
        assertFalse("the access token reached the log", capturedLog.contains(ACCESS_TOKEN))
    }

    @Test
    fun `should never log a request or response body`() {
        assumeTrue(BuildConfig.DEBUG)
        server.enqueue(probeResponse(SIGN_IN_RESPONSE_BODY))
        val signIn = Request.Builder()
            .url(server.url("/api/auth/login"))
            .post(SIGN_IN_REQUEST_BODY.toRequestBody(JSON_MEDIA_TYPE.toMediaType()))
            .build()

        sessionClient().newCall(signIn).execute().close()

        assertTrue("nothing was captured from the log", capturedLog.contains(PROBE_HEADER_VALUE))
        assertFalse("the request body reached the log", capturedLog.contains(PASSWORD))
        assertFalse("the response body reached the log", capturedLog.contains(ISSUED_REFRESH_TOKEN))
    }

    private companion object {
        const val ACCESS_TOKEN = "access-token-that-must-stay-out-of-the-log"
        const val REFRESH_TOKEN = "refresh-token-that-must-stay-out-of-the-log"
        const val PASSWORD = "password-that-must-stay-out-of-the-log"
        const val ISSUED_REFRESH_TOKEN = "issued-refresh-token-that-must-stay-out-of-the-log"
        const val PROBE_HEADER = "X-Probe"
        const val PROBE_HEADER_VALUE = "probe-header-value"
        const val JSON_MEDIA_TYPE = "application/json"
        const val SIGN_IN_REQUEST_BODY = """{"email":"user@example.com","password":"$PASSWORD"}"""
        const val SIGN_IN_RESPONSE_BODY = """{"tokens":{"refreshToken":"$ISSUED_REFRESH_TOKEN"}}"""
    }
}
