package com.bedrud.app.core.auth

import android.content.Context
import android.util.Log
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import com.bedrud.app.core.api.AuthApi
import com.bedrud.app.models.LoginResponse
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response

class PasskeyManager(
    private val context: Context,
    private val authApi: AuthApi,
    private val authManager: AuthManager
) {
    private val credentialManager = CredentialManager.create(context)
    private val gson = Gson()

    /**
     * Passkey-based login: fetch the WebAuthn challenge, present the credential picker, send the
     * signed assertion back, and save the session on success.
     */
    suspend fun loginWithPasskey(activityContext: Context): Result<LoginResponse> =
        ceremony(
            what = "passkey login",
            begin = { authApi.passkeyLoginBegin() },
            credentialJson = { optionsJson -> getAssertionJson(activityContext, optionsJson) },
            finish = { data -> authApi.passkeyLoginFinish(data) },
        ).mapCatching { response ->
            val body = response.body() ?: throw Exception("Empty login response")
            authManager.saveTokens(body.tokens)
            authManager.saveUser(body.user)
            body
        }

    /**
     * Passkey-based signup: fetch creation options for [email]/[name], create the credential on
     * device, send the attestation back, and save the session on success.
     */
    suspend fun signupWithPasskey(
        activityContext: Context,
        email: String,
        name: String
    ): Result<LoginResponse> =
        ceremony(
            what = "passkey signup",
            begin = { authApi.passkeySignupBegin(mapOf("email" to email, "name" to name)) },
            credentialJson = { optionsJson -> createAttestationJson(activityContext, optionsJson) },
            finish = { data -> authApi.passkeySignupFinish(data) },
        ).mapCatching { response ->
            val body = response.body() ?: throw Exception("Empty signup response")
            authManager.saveTokens(body.tokens)
            authManager.saveUser(body.user)
            body
        }

    /** Registers an additional passkey for the already-authenticated user. */
    suspend fun registerPasskey(activityContext: Context): Result<Unit> =
        ceremony(
            what = "passkey registration",
            begin = { authApi.passkeyRegisterBegin() },
            credentialJson = { optionsJson -> createAttestationJson(activityContext, optionsJson) },
            finish = { data -> authApi.passkeyRegisterFinish(data) },
        ).map { }

    /**
     * The WebAuthn round-trip every flow shares: begin call → challenge options → a credential
     * step on the main dispatcher → parse its JSON payload → finish call. Only the endpoints and
     * the credential step (get vs create) differ per flow. Failures come back as Result.failure
     * with a "$what …" message; cancellation rethrows.
     */
    private suspend fun <R> ceremony(
        what: String,
        begin: suspend () -> Response<*>,
        credentialJson: suspend (optionsJson: String) -> String,
        finish: suspend (Map<String, Any>) -> Response<R>,
    ): Result<Response<R>> = withContext(Dispatchers.IO) {
        try {
            val beginResponse = begin()
            if (!beginResponse.isSuccessful) {
                return@withContext Result.failure(
                    Exception("Failed to start $what: ${beginResponse.code()}")
                )
            }

            val options = beginResponse.body()
                ?: return@withContext Result.failure(Exception("Empty $what options"))

            val payloadJson = credentialJson(gson.toJson(options))

            @Suppress("UNCHECKED_CAST")
            val payload = gson.fromJson(payloadJson, Map::class.java) as Map<String, Any>

            val finishResponse = finish(payload)
            if (!finishResponse.isSuccessful) {
                return@withContext Result.failure(
                    Exception("$what verification failed: ${finishResponse.code()}")
                )
            }

            Result.success(finishResponse)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "$what failed", e)
            Result.failure(e)
        }
    }

    /** Presents the credential picker and returns the signed assertion JSON (login). */
    private suspend fun getAssertionJson(activityContext: Context, optionsJson: String): String {
        val request = GetCredentialRequest(listOf(GetPublicKeyCredentialOption(optionsJson)))
        val result = withContext(Dispatchers.Main) {
            credentialManager.getCredential(activityContext, request)
        }
        val credential = result.credential as? PublicKeyCredential
            ?: throw Exception("Unexpected credential type")
        return credential.authenticationResponseJson
    }

    /** Creates a credential on device and returns the attestation JSON (signup/register). */
    private suspend fun createAttestationJson(activityContext: Context, optionsJson: String): String {
        val result = withContext(Dispatchers.Main) {
            credentialManager.createCredential(
                activityContext,
                CreatePublicKeyCredentialRequest(optionsJson)
            )
        }
        val credential = result as? CreatePublicKeyCredentialResponse
            ?: throw Exception("Unexpected credential response type")
        return credential.registrationResponseJson
    }

    companion object {
        private const val TAG = "PasskeyManager"
    }
}
