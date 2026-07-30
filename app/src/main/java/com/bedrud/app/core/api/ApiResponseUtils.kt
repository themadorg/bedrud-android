package com.bedrud.app.core.api

import com.bedrud.app.core.auth.AuthManager
import com.bedrud.app.models.ApiError
import com.bedrud.app.models.LoginRequest
import com.google.gson.Gson
import com.google.gson.JsonObject
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
