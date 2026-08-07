package com.bedrud.app.core.api

import com.bedrud.app.models.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AuthApiTest : MockApiTest() {

    private lateinit var authApi: AuthApi

    @Before
    fun setUp() {
        authApi = api()
    }

    @Test
    fun `login sends POST to auth-login with correct body`() = runBlocking {
        val responseBody = gson.toJson(
            LoginResponse(
                tokens = AuthTokens(accessToken = "acc", refreshToken = "ref"),
                user = User(id = "u1", email = "a@b.com", name = "Alice")
            )
        )
        server.enqueue(MockResponse().setBody(responseBody).setResponseCode(200))

        val response = authApi.login(LoginRequest(email = "a@b.com", password = "pass"))

        assertRequest("POST", "/auth/login", listOf("a@b.com", "pass"))
        assertTrue(response.isSuccessful)
        assertEquals("acc", response.body()!!.tokens.accessToken)
        assertEquals("u1", response.body()!!.user.id)
    }

    @Test
    fun `register sends POST to auth-register and parses LoginResponse shape`() = runBlocking {
        val responseBody = gson.toJson(
            LoginResponse(
                tokens = AuthTokens(accessToken = "acc", refreshToken = "ref"),
                user = User(id = "u1", email = "a@b.com", name = "Alice")
            )
        )
        server.enqueue(MockResponse().setBody(responseBody).setResponseCode(200))

        val response = authApi.register(
            RegisterRequest(email = "a@b.com", password = "pass", name = "Alice")
        )

        assertRequest("POST", "/auth/register")
        assertTrue(response.isSuccessful)
        assertTrue(response.body()!!.has("tokens"))
        assertEquals("acc", response.body()!!.getAsJsonObject("tokens").get("accessToken").asString)
    }

    @Test
    fun `guestLogin sends POST to auth-guest-login`() = runBlocking {
        val responseBody = gson.toJson(
            LoginResponse(
                tokens = AuthTokens(accessToken = "acc", refreshToken = "ref"),
                user = User(id = "g1", email = "guest@b.com", name = "Guest")
            )
        )
        server.enqueue(MockResponse().setBody(responseBody).setResponseCode(200))

        val response = authApi.guestLogin(GuestLoginRequest(name = "Guest"))

        assertRequest("POST", "/auth/guest-login")
        assertTrue(response.isSuccessful)
    }

    @Test
    fun `refreshToken sends POST to auth-refresh with refresh_token key`() = runBlocking {
        val responseBody = gson.toJson(
            RefreshTokenResponse(accessToken = "new_acc", refreshToken = "new_ref")
        )
        server.enqueue(MockResponse().setBody(responseBody).setResponseCode(200))

        val response = authApi.refreshToken(RefreshTokenRequest(refreshToken = "old_ref"))

        assertRequest("POST", "/auth/refresh", listOf("refresh_token", "old_ref"))
        assertTrue(response.isSuccessful)
        assertEquals("new_acc", response.body()!!.accessToken)
    }

    @Test
    fun `getMe sends GET to auth-me and parses MeResponse`() = runBlocking {
        val responseBody = gson.toJson(
            MeResponse(id = "u1", email = "a@b.com", name = "Alice")
        )
        server.enqueue(MockResponse().setBody(responseBody).setResponseCode(200))

        val response = authApi.getMe()

        assertRequest("GET", "/auth/me")
        assertTrue(response.isSuccessful)
        assertEquals("u1", response.body()!!.id)
        assertEquals("Alice", response.body()!!.name)
    }

    @Test
    fun `forgotPassword sends POST to auth-forgot-password with email body`() = runBlocking {
        // Server answers uniformly (no account enumeration); the client only needs a 2xx.
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"message\":\"ok\"}"))

        val response = authApi.forgotPassword(ForgotPasswordRequest(email = "a@b.com"))

        assertRequest("POST", "/auth/forgot-password", listOf("a@b.com"))
        assertTrue(response.isSuccessful)
    }
}
