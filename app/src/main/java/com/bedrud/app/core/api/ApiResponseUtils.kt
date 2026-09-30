package com.bedrud.app.core.api

import com.bedrud.app.core.auth.AuthManager
import com.bedrud.app.core.auth.SignInNoticeRelay
import com.bedrud.app.models.ApiError
import com.bedrud.app.models.LoginRequest
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import retrofit2.Response

/**
 * Outcome messages carry the server's own text when it sent any, and null otherwise — the caller
 * shows its localized fallback for null, keeping user-facing text in strings.xml instead of here.
 */
sealed class RegisterOutcome {
    data object AccountCreated : RegisterOutcome()
    data class VerificationRequired(val email: String, val message: String?) : RegisterOutcome()
    data class Failed(val message: String?) : RegisterOutcome()
}

sealed class LoginOutcome {
    data object Success : LoginOutcome()
    data class VerificationRequired(val email: String, val message: String?) : LoginOutcome()
    data class Failed(val message: String?) : LoginOutcome()
}

private val gson = Gson()

/** The server's error/message text from a raw error body, or null when it has none worth showing. */
private fun parseErrorText(raw: String): String? {
    return try {
        val err = gson.fromJson(raw, ApiError::class.java)
        err.message?.takeIf { it.isNotBlank() } ?: err.error.takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }
}

/**
 * The server's error/message text for a failed response, or null when it sent none.
 *
 * Reads the error body, which is single-shot — call this at most once per response, and never
 * after something else consumed the body.
 */
fun parseApiErrorMessage(response: Response<*>): String? {
    return try {
        val raw = response.errorBody()?.string() ?: return null
        parseErrorText(raw)
    } catch (_: Exception) {
        null
    }
}

private fun JsonObject.requiresVerification(): Boolean =
    has("requiresVerification") && get("requiresVerification").asBoolean

private fun JsonObject.verificationEmail(): String = get("email")?.asString.orEmpty()

fun parseRegisterResponse(response: Response<JsonObject>): RegisterOutcome {
    if (!response.isSuccessful) {
        return RegisterOutcome.Failed(parseApiErrorMessage(response))
    }

    val json = response.body() ?: return RegisterOutcome.Failed(null)

    if (json.requiresVerification()) {
        return RegisterOutcome.VerificationRequired(
            email = json.verificationEmail(),
            message = json.get("message")?.asString
        )
    }

    return RegisterOutcome.AccountCreated
}

/**
 * Replaces the stored user with the server's current record of it, so what the app shows about the
 * account follows changes made since sign-in, on another device or in an older version of the app
 * that stored less of the record.
 *
 * Silent on failure: the stored record is still the best the app has, and nothing the user asked
 * for has failed.
 */
suspend fun refreshCurrentUser(authApi: AuthApi, authManager: AuthManager) {
    val user = apiBody("", onError = {}) { authApi.getMe() } ?: return
    authManager.replaceUser(user)
}

suspend fun performLogin(
    authApi: AuthApi,
    authManager: AuthManager,
    email: String,
    password: String
): LoginOutcome {
    val response = authApi.login(LoginRequest(email = email, password = password))
    if (response.isSuccessful) {
        val body = response.body() ?: return LoginOutcome.Failed(null)
        authManager.saveTokens(body.tokens)
        authManager.saveUser(body.user)
        return LoginOutcome.Success
    }

    return try {
        // The error body is single-shot, so read it once and parse both concerns from the text.
        val raw = response.errorBody()?.string() ?: return LoginOutcome.Failed(null)
        val json = gson.fromJson(raw, JsonObject::class.java)
        if (json != null && json.requiresVerification()) {
            LoginOutcome.VerificationRequired(
                email = json.verificationEmail(),
                message = json.get("error")?.asString
            )
        } else {
            LoginOutcome.Failed(parseErrorText(raw))
        }
    } catch (_: Exception) {
        LoginOutcome.Failed(null)
    }
}

/**
 * Signs straight back in with [newPassword] once the server has accepted it, and returns whether
 * that worked.
 *
 * The server ends every session of the account on a password change, this one included: it clears
 * the stored refresh token and revokes the access token the change was sent with. Left alone, the
 * app would carry on until its next request, which would then sign the user out with no reason
 * given. Signing in with the new password keeps them where they are.
 *
 * When that fails (no email to sign in with, the server unreachable, or the sign-in refused), the
 * user is signed out at once, and [signInAgainNotice] goes to the sign-in screen they land on
 * through [signInNoticeRelay], so the sign-out comes with its reason.
 */
suspend fun signBackInAfterPasswordChange(
    authApi: AuthApi,
    authManager: AuthManager,
    email: String?,
    newPassword: String,
    signInNoticeRelay: SignInNoticeRelay,
    signInAgainNotice: String,
): Boolean {
    val outcome = if (email.isNullOrBlank()) {
        null
    } else {
        try {
            performLogin(authApi, authManager, email, newPassword)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }
    if (outcome is LoginOutcome.Success) return true

    // Reported before signing out, which takes the app to the sign-in screen that shows it.
    signInNoticeRelay.report(signInAgainNotice)
    authManager.logout()
    return false
}
