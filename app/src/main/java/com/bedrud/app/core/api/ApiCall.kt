package com.bedrud.app.core.api

import kotlinx.coroutines.CancellationException
import retrofit2.Response

/**
 * Runs one Retrofit call and returns its body, or null after reporting the failure to [onError].
 *
 * Every failure funnels into [onError] with the text to show: an exception's message, or
 * [fallbackMessage] when the exception carries none, the status is non-2xx, or a 2xx response
 * has no body. Cancellation always rethrows — the hand-rolled try/catch blocks this replaces
 * silently swallowed coroutine cancellation.
 */
suspend fun <T : Any> apiBody(
    fallbackMessage: String,
    onError: suspend (String) -> Unit,
    call: suspend () -> Response<T>,
): T? {
    return try {
        val response = call()
        if (response.isSuccessful) {
            val body = response.body()
            if (body == null) onError(fallbackMessage)
            body
        } else {
            onError(fallbackMessage)
            null
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        onError(e.message ?: fallbackMessage)
        null
    }
}

/**
 * [apiBody] for calls made only for their effect (deletes, updates): true on 2xx, false after
 * reporting the failure to [onError].
 */
suspend fun apiAction(
    fallbackMessage: String,
    onError: suspend (String) -> Unit,
    call: suspend () -> Response<*>,
): Boolean {
    return try {
        val response = call()
        if (response.isSuccessful) {
            true
        } else {
            onError(fallbackMessage)
            false
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        onError(e.message ?: fallbackMessage)
        false
    }
}
