package com.bedrud.app.core.api

import com.bedrud.app.BuildConfig
import com.bedrud.app.core.auth.AuthManager
import com.bedrud.app.models.RefreshTokenRequest
import com.google.gson.GsonBuilder
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/** Connect/read/write timeout applied to every Bedrud API OkHttp client. */
private const val DEFAULT_TIMEOUT_SECONDS = 30L

/** Cap on 401-driven token-refresh retries before forcing logout, to avoid infinite loops. */
private const val MAX_REFRESH_ATTEMPTS = 2

/**
 * Interceptor that attaches the JWT access token to every outgoing request.
 */
class AuthInterceptor(
    private val authManager: AuthManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()

        val accessToken = authManager.getAccessToken()
        if (accessToken.isNullOrBlank()) {
            return chain.proceed(original)
        }

        val authenticatedRequest = original.newBuilder()
            .header(ApiHeaders.AUTHORIZATION, ApiHeaders.bearer(accessToken))
            .build()

        return chain.proceed(authenticatedRequest)
    }
}

/**
 * Authenticator that handles 401 responses by refreshing the JWT token
 * and retrying the original request with the new token.
 */
class TokenAuthenticator(
    private val authManager: AuthManager,
    private val baseURL: String,
    private val authApiProvider: () -> AuthApi
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // Avoid infinite retry loops
        if (responseCount(response) >= MAX_REFRESH_ATTEMPTS) {
            authManager.logout()
            return null
        }

        val refreshToken = authManager.getRefreshToken() ?: run {
            authManager.logout()
            return null
        }

        // Perform synchronous token refresh
        val refreshCall = authApiProvider().let { _ ->
            // Use a separate retrofit instance without the authenticator to avoid recursion
            val refreshRetrofit = Retrofit.Builder()
                .baseUrl(baseURL.trimEnd('/') + "/")
                .addConverterFactory(GsonConverterFactory.create())
                .client(
                    OkHttpClient.Builder()
                        .connectTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .readTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .build()
                )
                .build()

            val refreshApi = refreshRetrofit.create(AuthApi::class.java)
            try {
                val refreshResponse = kotlinx.coroutines.runBlocking {
                    refreshApi.refreshToken(RefreshTokenRequest(refreshToken))
                }
                if (refreshResponse.isSuccessful) {
                    refreshResponse.body()
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }

        if (refreshCall != null) {
            authManager.saveTokens(refreshCall.accessToken, refreshCall.refreshToken)

            return response.request.newBuilder()
                .header(ApiHeaders.AUTHORIZATION, ApiHeaders.bearer(refreshCall.accessToken))
                .build()
        }

        // Refresh failed, force logout
        authManager.logout()
        return null
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}

/**
 * Factory that creates configured Retrofit instances for the Bedrud API.
 */
class ApiClientFactory(private val baseURL: String) {

    fun createOkHttpClient(
        authInterceptor: AuthInterceptor,
        tokenAuthenticator: TokenAuthenticator
    ): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .authenticator(tokenAuthenticator)
            .connectTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    fun createRetrofit(okHttpClient: OkHttpClient): Retrofit {
        val gson = GsonBuilder()
            .setLenient()
            .create()

        return Retrofit.Builder()
            .baseUrl(baseURL.trimEnd('/') + "/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    inline fun <reified T> createApi(retrofit: Retrofit): T {
        return retrofit.create(T::class.java)
    }
}
