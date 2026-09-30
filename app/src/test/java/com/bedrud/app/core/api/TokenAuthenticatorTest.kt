package com.bedrud.app.core.api

import com.bedrud.app.core.auth.AuthManager
import com.bedrud.app.models.RefreshTokenResponse
import com.bedrud.app.testutil.InMemorySharedPreferences
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.net.HttpURLConnection
import java.util.concurrent.TimeUnit

private const val OLD_ACCESS_TOKEN = "old_access"
private const val OLD_REFRESH_TOKEN = "old_refresh"
private const val NEW_ACCESS_TOKEN = "new_access"
private const val NEW_REFRESH_TOKEN = "new_refresh"

/** Any endpoint that needs a signed-in session; the server under test decides how it answers. */
private const val PROTECTED_PATH = "/api/test"

/** Connect and read timeout for the test client, short so a hung call fails the test quickly. */
private const val CLIENT_TIMEOUT_SECONDS = 5L

class TokenAuthenticatorTest : MockApiTest() {

    private lateinit var prefs: InMemorySharedPreferences
    private lateinit var authManager: AuthManager

    @Before
    fun setUp() {
        prefs = InMemorySharedPreferences()
        authManager = AuthManager(prefs)
    }

    private fun buildAuthenticator(): TokenAuthenticator =
        TokenAuthenticator(
            authManager = authManager,
            baseURL = server.url("/").toString(),
        )

    /** A client wired as the app's own: the stored access token on every request, and [authenticator] on a 401. */
    private fun buildClient(authenticator: TokenAuthenticator = buildAuthenticator()): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(authManager))
            .authenticator(authenticator)
            .connectTimeout(CLIENT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(CLIENT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

    private fun protectedRequest(): Request =
        Request.Builder().url(server.url(PROTECTED_PATH)).build()

    private fun refreshBody(): String =
        gson.toJson(RefreshTokenResponse(accessToken = NEW_ACCESS_TOKEN, refreshToken = NEW_REFRESH_TOKEN))

    @Test
    fun `successful refresh saves new tokens and retries with new token`() {
        authManager.saveTokens(OLD_ACCESS_TOKEN, OLD_REFRESH_TOKEN)

        // First: 401 on the original request
        server.enqueue(MockResponse().setResponseCode(HttpURLConnection.HTTP_UNAUTHORIZED))
        // Second: refresh endpoint returns new tokens (called by internal Retrofit)
        server.enqueue(MockResponse().setBody(refreshBody()).setResponseCode(HttpURLConnection.HTTP_OK))
        // Third: retried request succeeds
        server.enqueue(MockResponse().setBody("success").setResponseCode(HttpURLConnection.HTTP_OK))

        val response = buildClient().newCall(protectedRequest()).execute()

        assertEquals(HttpURLConnection.HTTP_OK, response.code)
        assertEquals(NEW_ACCESS_TOKEN, authManager.getAccessToken())
        assertEquals(NEW_REFRESH_TOKEN, authManager.getRefreshToken())

        // Verify the retry had the new token
        server.takeRequest() // original
        server.takeRequest() // refresh call
        val retry = server.takeRequest()
        assertEquals(ApiHeaders.bearer(NEW_ACCESS_TOKEN), retry.getHeader(ApiHeaders.AUTHORIZATION))
    }

    @Test
    fun `no refresh token calls logout and returns null`() {
        // Don't save any tokens → getRefreshToken() returns null
        val client = OkHttpClient.Builder()
            .authenticator(buildAuthenticator())
            .build()

        server.enqueue(MockResponse().setResponseCode(HttpURLConnection.HTTP_UNAUTHORIZED))

        val response = client.newCall(protectedRequest()).execute()

        assertEquals(HttpURLConnection.HTTP_UNAUTHORIZED, response.code)
        assertFalse(authManager.isLoggedIn.value)
    }

    @Test
    fun `failed refresh returns 401 and logs out`() {
        authManager.saveTokens(OLD_ACCESS_TOKEN, OLD_REFRESH_TOKEN)

        // First: 401 on the original request
        server.enqueue(MockResponse().setResponseCode(HttpURLConnection.HTTP_UNAUTHORIZED))
        // Second: refresh endpoint also fails
        server.enqueue(MockResponse().setResponseCode(HttpURLConnection.HTTP_UNAUTHORIZED))

        val response = buildClient().newCall(protectedRequest()).execute()

        assertEquals(HttpURLConnection.HTTP_UNAUTHORIZED, response.code)
        assertFalse(authManager.isLoggedIn.value)
    }

    @Test
    fun `max retries exceeded calls logout`() {
        authManager.saveTokens(OLD_ACCESS_TOKEN, OLD_REFRESH_TOKEN)

        // First: 401 on original
        server.enqueue(MockResponse().setResponseCode(HttpURLConnection.HTTP_UNAUTHORIZED))
        // Second: refresh succeeds with new tokens
        server.enqueue(MockResponse().setBody(refreshBody()).setResponseCode(HttpURLConnection.HTTP_OK))
        // Third: retried request also 401 → triggers authenticator again with responseCount >= 2
        server.enqueue(MockResponse().setResponseCode(HttpURLConnection.HTTP_UNAUTHORIZED))

        val response = buildClient().newCall(protectedRequest()).execute()

        // After second 401, responseCount >= 2, so authenticator returns null
        assertEquals(HttpURLConnection.HTTP_UNAUTHORIZED, response.code)
        assertFalse(authManager.isLoggedIn.value)
    }
}
