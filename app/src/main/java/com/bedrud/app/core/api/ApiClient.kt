package com.bedrud.app.core.api

import com.bedrud.app.BuildConfig
import com.bedrud.app.core.auth.AuthManager
import com.bedrud.app.models.RefreshTokenRequest
import com.bedrud.app.models.RefreshTokenResponse
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.Strictness
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.net.HttpURLConnection
import java.util.concurrent.TimeUnit

/** Connect/read/write timeout applied to every Bedrud API OkHttp client. */
private const val DEFAULT_TIMEOUT_SECONDS = 30L

/** Cap on 401-driven token-refresh retries before forcing logout, to avoid infinite loops. */
private const val MAX_REFRESH_ATTEMPTS = 2

/** HTTP 429 Too Many Requests, which [HttpURLConnection] has no constant for. */
internal const val HTTP_TOO_MANY_REQUESTS = 429

/**
 * Client errors from `auth/refresh` that say nothing about the refresh token: the request timed
 * out, or the server is turning requests away for arriving too fast. Every other 4xx turns it down.
 */
private val REFRESH_RETRYABLE_CLIENT_ERRORS = setOf(
    HttpURLConnection.HTTP_CLIENT_TIMEOUT,
    HTTP_TOO_MANY_REQUESTS,
)

/**
 * Gson as every Bedrud client reads a response. Shared so a payload parses the same way whichever
 * client fetched it, rather than each builder deciding for itself.
 */
private fun lenientGson(): Gson = GsonBuilder()
    .setStrictness(Strictness.LENIENT)
    .create()

/**
 * A Retrofit client with nothing attached: no auth header, no token authenticator, no logging.
 *
 * Two calls need one. The token refresh cannot carry the authenticator it was triggered by, or a
 * refresh that itself answers 401 re-enters it. The health probe runs against a server the app has
 * no account on yet, so there is no session to attach.
 *
 * [timeoutSeconds] defaults to the app-wide API timeout; a caller with a reason to wait less passes
 * its own. The trailing slash Retrofit demands of a base URL is applied here, so no caller repeats
 * it.
 */
internal fun plainRetrofit(
    baseURL: String,
    timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS,
): Retrofit = Retrofit.Builder()
    .baseUrl(baseURL.trimEnd('/') + "/")
    .client(
        OkHttpClient.Builder()
            .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .build()
    )
    .addConverterFactory(GsonConverterFactory.create(lenientGson()))
    .build()

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

/** What `auth/refresh` made of the stored refresh token. */
private sealed interface RefreshOutcome {

    /** A new token pair, replacing the stored one. */
    data class Refreshed(val tokens: RefreshTokenResponse) : RefreshOutcome

    /**
     * The session is over: the server turned the refresh token down (expired, revoked, already
     * rotated, or its account can no longer sign in), or took it without sending a usable pair back.
     */
    data object Ended : RefreshOutcome

    /**
     * The server could not judge the refresh token this time: an error of its own, a timeout, or
     * rate limiting. The token may well be good on the next try.
     */
    data object Unavailable : RefreshOutcome
}

/**
 * Authenticator that handles 401 responses by refreshing the JWT token
 * and retrying the original request with the new token.
 *
 * Refreshes run one at a time. The server rotates the refresh token on every refresh and turns the
 * old one down from then on, so two requests refreshing the same expired session would spend the
 * same refresh token twice, and the second refresh would end the session the first had just
 * renewed. A request that failed on an access token another request has since replaced retries
 * with the replacement instead of refreshing again.
 *
 * Only the server turning the refresh token down ends the session. A refresh that never reached
 * the server fails its call with the network error, and one the server could not answer hands the
 * 401 back; both leave the tokens in place for the next request to refresh with.
 */
class TokenAuthenticator(
    private val authManager: AuthManager,
    private val baseURL: String,
) : Authenticator {

    /** Held for the whole of a refresh, so a 401 arriving meanwhile waits for its result. */
    private val refreshLock = Any()

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    /** [request] again, carrying [accessToken] in place of whatever it was sent with. */
    private fun withAccessToken(request: Request, accessToken: String): Request =
        request.newBuilder()
            .header(ApiHeaders.AUTHORIZATION, ApiHeaders.bearer(accessToken))
            .build()

    /**
     * Sends [refreshToken] to `auth/refresh`, on a plain client so it cannot recurse back through
     * this authenticator.
     *
     * An [IOException] is not caught: the refresh never reached the server, or its answer never
     * arrived, so nothing is known about the token, and the call that needed the refresh fails as
     * the network failure it is.
     */
    @Throws(IOException::class)
    private fun refresh(refreshToken: String): RefreshOutcome {
        val refreshApi = plainRetrofit(baseURL).create(AuthApi::class.java)
        val refreshResponse = try {
            runBlocking { refreshApi.refreshToken(RefreshTokenRequest(refreshToken)) }
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            // The server answered, but reading the answer failed: most likely a success whose body
            // would not parse. A success has already rotated the token, so it is spent either way.
            return RefreshOutcome.Ended
        }
        val code = refreshResponse.code()
        return when {
            refreshResponse.isSuccessful ->
                refreshResponse.body()?.let(RefreshOutcome::Refreshed) ?: RefreshOutcome.Ended
            code >= HttpURLConnection.HTTP_INTERNAL_ERROR || code in REFRESH_RETRYABLE_CLIENT_ERRORS ->
                RefreshOutcome.Unavailable
            else -> RefreshOutcome.Ended
        }
    }

    override fun authenticate(route: Route?, response: Response): Request? {
        // Avoid infinite retry loops
        if (responseCount(response) >= MAX_REFRESH_ATTEMPTS) {
            authManager.logout()
            return null
        }

        synchronized(refreshLock) {
            // Another request refreshed while this one was out on the token it replaced.
            val storedAccessToken = authManager.getAccessToken()
            if (!storedAccessToken.isNullOrBlank() &&
                response.request.header(ApiHeaders.AUTHORIZATION) != ApiHeaders.bearer(storedAccessToken)
            ) {
                return withAccessToken(response.request, storedAccessToken)
            }

            val refreshToken = authManager.getRefreshToken() ?: run {
                authManager.logout()
                return null
            }

            return when (val outcome = refresh(refreshToken)) {
                is RefreshOutcome.Refreshed -> {
                    authManager.saveTokens(outcome.tokens.accessToken, outcome.tokens.refreshToken)
                    withAccessToken(response.request, outcome.tokens.accessToken)
                }
                RefreshOutcome.Ended -> {
                    authManager.logout()
                    null
                }
                // The 401 goes back to the caller as it came, and the stored tokens stay for the
                // next request to refresh with.
                RefreshOutcome.Unavailable -> null
            }
        }
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
        return Retrofit.Builder()
            .baseUrl(baseURL.trimEnd('/') + "/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(lenientGson()))
            .build()
    }

    inline fun <reified T> createApi(retrofit: Retrofit): T {
        return retrofit.create(T::class.java)
    }
}
