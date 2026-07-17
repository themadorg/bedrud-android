package com.bedrud.app.core.api

import com.bedrud.app.core.auth.AuthManager
import com.bedrud.app.models.ApiError
import com.bedrud.app.models.LoginRequest
import com.google.gson.Gson
import com.google.gson.JsonObject
import retrofit2.Response

sealed class RegisterOutcome {
    data object AccountCreated : RegisterOutcome()
    data class VerificationRequired(val email: String, val message: String) : RegisterOutcome()
    data class Failed(val message: String) : RegisterOutcome()
}

sealed class LoginOutcome {
    data object Success : LoginOutcome()
    data class VerificationRequired(val email: String, val message: String) : LoginOutcome()
    data class Failed(val message: String) : LoginOutcome()
}

private val gson = Gson()

fun parseApiErrorMessage(response: Response<*>, fallback: String): String {
    return try {
        val raw = response.errorBody()?.string() ?: return fallback
        val err = gson.fromJson(raw, ApiError::class.java)
        err.message?.takeIf { it.isNotBlank() } ?: err.error.ifBlank { fallback }
    } catch (_: Exception) {
        fallback
    }
}

fun parseRegisterResponse(response: Response<JsonObject>): RegisterOutcome {
    if (!response.isSuccessful) {
        return RegisterOutcome.Failed(
            parseApiErrorMessage(response, "Registration failed. Please try again.")
        )
    }

    val json = response.body() ?: return RegisterOutcome.Failed("Empty server response")

    if (json.has("requiresVerification") && json.get("requiresVerification").asBoolean) {
        return RegisterOutcome.VerificationRequired(
            email = json.get("email")?.asString.orEmpty(),
            message = json.get("message")?.asString
                ?: "Please check your email to verify your account"
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
        val body = response.body() ?: return LoginOutcome.Failed("Empty login response")
        authManager.saveTokens(body.tokens)
        authManager.saveUser(body.user)
        return LoginOutcome.Success
    }

    return try {
        val raw = response.errorBody()?.string()
        if (raw != null) {
            val json = gson.fromJson(raw, JsonObject::class.java)
            if (json.has("requiresVerification") && json.get("requiresVerification").asBoolean) {
                return LoginOutcome.VerificationRequired(
                    email = json.get("email")?.asString.orEmpty(),
                    message = json.get("error")?.asString
                        ?: "Please verify your email before signing in"
                )
            }
        }
        LoginOutcome.Failed(
            parseApiErrorMessage(response, "Sign-in failed. Please try again.")
        )
    } catch (_: Exception) {
        LoginOutcome.Failed("Sign-in failed. Please try again.")
    }
}