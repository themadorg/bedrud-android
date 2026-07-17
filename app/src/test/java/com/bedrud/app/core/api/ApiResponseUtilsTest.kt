package com.bedrud.app.core.api

import com.bedrud.app.core.auth.AuthManager
import com.bedrud.app.models.AuthTokens
import com.bedrud.app.models.LoginResponse
import com.bedrud.app.models.User
import com.bedrud.app.testutil.InMemorySharedPreferences
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class ApiResponseUtilsTest {

    private val gson = Gson()
    private lateinit var server: MockWebServer
    private lateinit var authApi: AuthApi
    private lateinit var authManager: AuthManager

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        authApi = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AuthApi::class.java)
        authManager = AuthManager(InMemorySharedPreferences())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `parseRegisterResponse returns AccountCreated for tokens response`() {
        val json = gson.fromJson(
            """
            {
                "tokens": {"accessToken": "acc", "refreshToken": "ref"},
                "user": {"id": "u1", "email": "a@b.com", "name": "Alice"}
            }
            """.trimIndent(),
            JsonObject::class.java
        )
        val response = Response.success(json)

        val outcome = parseRegisterResponse(response)

        assertTrue(outcome is RegisterOutcome.AccountCreated)
    }

    @Test
    fun `parseRegisterResponse returns VerificationRequired`() {
        val json = gson.fromJson(
            """
            {
                "requiresVerification": true,
                "message": "Check your inbox",
                "email": "a@b.com"
            }
            """.trimIndent(),
            JsonObject::class.java
        )
        val response = Response.success(json)

        val outcome = parseRegisterResponse(response)

        assertTrue(outcome is RegisterOutcome.VerificationRequired)
        val verification = outcome as RegisterOutcome.VerificationRequired
        assertEquals("Check your inbox", verification.message)
        assertEquals("a@b.com", verification.email)
    }

    @Test
    fun `parseRegisterResponse returns Failed with server error message`() {
        val body = """{"error":"user already exists"}""".toResponseBody()
        val response = Response.error<JsonObject>(400, body)

        val outcome = parseRegisterResponse(response)

        assertTrue(outcome is RegisterOutcome.Failed)
        assertEquals("user already exists", (outcome as RegisterOutcome.Failed).message)
    }

    @Test
    fun `performLogin stores tokens and returns Success`() = runBlocking {
        val loginBody = gson.toJson(
            LoginResponse(
                tokens = AuthTokens(accessToken = "acc", refreshToken = "ref"),
                user = User(id = "u1", email = "a@b.com", name = "Alice")
            )
        )
        server.enqueue(MockResponse().setBody(loginBody).setResponseCode(200))

        val outcome = performLogin(authApi, authManager, "a@b.com", "secret-pass")

        assertTrue(outcome is LoginOutcome.Success)
        assertEquals("acc", authManager.getAccessToken())
        assertEquals("ref", authManager.getRefreshToken())
        assertEquals("Alice", authManager.currentUser.value?.name)
    }

    @Test
    fun `performLogin returns VerificationRequired on 403`() = runBlocking {
        val body = """
            {
                "error": "Please verify your email before signing in",
                "requiresVerification": true,
                "email": "a@b.com"
            }
        """.trimIndent()
        server.enqueue(MockResponse().setBody(body).setResponseCode(403))

        val outcome = performLogin(authApi, authManager, "a@b.com", "secret-pass")

        assertTrue(outcome is LoginOutcome.VerificationRequired)
        assertEquals(
            "Please verify your email before signing in",
            (outcome as LoginOutcome.VerificationRequired).message
        )
    }
}