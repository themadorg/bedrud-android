package com.bedrud.app.core.api

import com.bedrud.app.core.auth.AuthManager
import com.bedrud.app.core.auth.SignInNoticeRelay
import com.bedrud.app.models.AuthTokens
import com.bedrud.app.models.LoginResponse
import com.bedrud.app.models.User
import com.bedrud.app.testutil.InMemorySharedPreferences
import com.google.gson.JsonObject
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.net.HttpURLConnection

/** When a user's password last changed, as the server writes the time. */
private const val PASSWORD_CHANGED_AT = "2026-09-01T10:00:00Z"

/** The password the user has just changed to, and the tokens signing in with it earns. */
private const val NEW_PASSWORD = "a-new-password-1234"
private const val NEW_ACCESS_TOKEN = "access-after-change"
private const val NEW_REFRESH_TOKEN = "refresh-after-change"

/** The notice Settings hands the sign-in screen when it cannot sign back in. */
private const val SIGN_IN_AGAIN_NOTICE = "Your password was changed. Sign in with your new password."

class ApiResponseUtilsTest : MockApiTest() {

    private lateinit var authApi: AuthApi
    private lateinit var authManager: AuthManager

    @Before
    fun setUp() {
        authApi = api()
        authManager = AuthManager(InMemorySharedPreferences())
    }

    /** Signs in a passkey account whose stored record says it never set a password. */
    private fun signInPasskeyUser(): User {
        val user = User(id = "u1", email = "a@b.com", name = "Alice", provider = "passkey")
        authManager.saveTokens("acc", "ref")
        authManager.saveUser(user)
        return user
    }

    @Test
    fun `signBackInAfterPasswordChange signs in with the new password and keeps the user signed in`() = runBlocking {
        val stored = signInPasskeyUser()
        val relay = SignInNoticeRelay()
        val signedIn = stored.copy(passwordChangedAt = PASSWORD_CHANGED_AT)
        server.enqueue(
            MockResponse().setBody(
                gson.toJson(LoginResponse(tokens = AuthTokens(NEW_ACCESS_TOKEN, NEW_REFRESH_TOKEN), user = signedIn))
            )
        )

        val signedBackIn = signBackInAfterPasswordChange(
            authApi, authManager, stored.email, NEW_PASSWORD, relay, SIGN_IN_AGAIN_NOTICE
        )

        assertTrue(signedBackIn)
        assertRequest("POST", "/auth/login", listOf(stored.email, NEW_PASSWORD))
        assertEquals(NEW_ACCESS_TOKEN, authManager.getAccessToken())
        assertEquals(NEW_REFRESH_TOKEN, authManager.getRefreshToken())
        assertEquals(signedIn, authManager.currentUser.value)
        assertNull(relay.message.value)
    }

    @Test
    fun `signBackInAfterPasswordChange signs out and says why when the new password is turned down`() = runBlocking {
        val stored = signInPasskeyUser()
        val relay = SignInNoticeRelay()
        server.enqueue(
            MockResponse().setBody("""{"error":"invalid credentials"}""")
                .setResponseCode(HttpURLConnection.HTTP_UNAUTHORIZED)
        )

        val signedBackIn = signBackInAfterPasswordChange(
            authApi, authManager, stored.email, NEW_PASSWORD, relay, SIGN_IN_AGAIN_NOTICE
        )

        assertFalse(signedBackIn)
        assertFalse(authManager.isLoggedIn.value)
        assertNull(authManager.currentUser.value)
        assertEquals(SIGN_IN_AGAIN_NOTICE, relay.message.value)
    }

    @Test
    fun `signBackInAfterPasswordChange signs out and says why when the server cannot be reached`() = runBlocking {
        val stored = signInPasskeyUser()
        val relay = SignInNoticeRelay()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        val signedBackIn = signBackInAfterPasswordChange(
            authApi, authManager, stored.email, NEW_PASSWORD, relay, SIGN_IN_AGAIN_NOTICE
        )

        assertFalse(signedBackIn)
        assertFalse(authManager.isLoggedIn.value)
        assertEquals(SIGN_IN_AGAIN_NOTICE, relay.message.value)
    }

    @Test
    fun `signBackInAfterPasswordChange signs out and says why when there is no email to sign in with`() = runBlocking {
        signInPasskeyUser()
        val relay = SignInNoticeRelay()

        val signedBackIn = signBackInAfterPasswordChange(
            authApi, authManager, email = "", NEW_PASSWORD, relay, SIGN_IN_AGAIN_NOTICE
        )

        assertFalse(signedBackIn)
        assertEquals(0, server.requestCount)
        assertFalse(authManager.isLoggedIn.value)
        assertEquals(SIGN_IN_AGAIN_NOTICE, relay.message.value)
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
    fun `refreshCurrentUser stores the server's record of the signed-in user`() = runBlocking {
        val stored = signInPasskeyUser()
        val serverRecord = stored.copy(passwordChangedAt = PASSWORD_CHANGED_AT)
        server.enqueue(MockResponse().setBody(gson.toJson(serverRecord)).setResponseCode(200))

        refreshCurrentUser(authApi, authManager)

        assertRequest("GET", "/auth/me")
        assertEquals(serverRecord, authManager.currentUser.value)
    }

    @Test
    fun `refreshCurrentUser keeps the stored user when the server answers with an error`() = runBlocking {
        val stored = signInPasskeyUser()
        server.enqueue(
            MockResponse().setBody("""{"error":"Failed to get user"}""")
                .setResponseCode(HttpURLConnection.HTTP_INTERNAL_ERROR)
        )

        refreshCurrentUser(authApi, authManager)

        assertEquals(stored, authManager.currentUser.value)
    }

    @Test
    fun `refreshCurrentUser keeps the stored user when the server cannot be reached`() = runBlocking {
        val stored = signInPasskeyUser()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        refreshCurrentUser(authApi, authManager)

        assertEquals(stored, authManager.currentUser.value)
    }

    @Test
    fun `parseRegisterResponse Failed carries null when server sent no message`() {
        val response = Response.error<JsonObject>(500, "".toResponseBody())

        val outcome = parseRegisterResponse(response)

        assertTrue(outcome is RegisterOutcome.Failed)
        assertNull((outcome as RegisterOutcome.Failed).message)
    }

    @Test
    fun `performLogin Failed carries the server error text`() = runBlocking {
        server.enqueue(
            MockResponse().setBody("""{"error":"bad credentials"}""").setResponseCode(401)
        )

        val outcome = performLogin(authApi, authManager, "a@b.com", "wrong-password")

        assertTrue(outcome is LoginOutcome.Failed)
        assertEquals("bad credentials", (outcome as LoginOutcome.Failed).message)
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