package com.bedrud.app.core.api

import kotlinx.coroutines.CancellationException
import retrofit2.Response

/**
 * Runs one Retrofit call and returns its body, or null after reporting the failure to [onError].
 *
 * Every failure funnels into [onError] with the text to show. A non-2xx status reports the server's
 * own explanation when it sent one — a 403 saying "Not available for guest accounts" is far more
 * use than a generic "couldn't do that" — and [fallbackMessage] when it didn't, or when a 2xx
 * response has no body. For a thrown exception it is the localized message from [classifyError]
 * when it returns one, else the exception's own message, else [fallbackMessage]; pass a
 * [classifyError] (e.g. `Throwable::toUserMessage`) to keep raw exception text out of the UI.
 * Cancellation always rethrows — the hand-rolled try/catch blocks this replaces silently swallowed
 * coroutine cancellation.
 *
 * Server text is the API's own English prose, so it is not localized; per-locale wording would need
 * server-side error codes. The localized [fallbackMessage] still covers every response without it.
 */
suspend fun <T : Any> apiBody(
    fallbackMessage: String,
    onError: suspend (String) -> Unit,
    classifyError: (Throwable) -> String? = { null },
    call: suspend () -> Response<T>,
): T? {
    return try {
        val response = call()
        if (response.isSuccessful) {
            val body = response.body()
            if (body == null) onError(fallbackMessage)
            body
        } else {
            onError(parseApiErrorMessage(response) ?: fallbackMessage)
            null
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        onError(classifyError(e) ?: e.message ?: fallbackMessage)
        null
    }
}

/**
 * [apiBody] for calls made only for their effect (deletes, updates): true on 2xx, false after
 * reporting the failure to [onError]. Surfaces the server's error text on non-2xx exactly as
 * [apiBody] does.
 */
suspend fun apiAction(
    fallbackMessage: String,
    onError: suspend (String) -> Unit,
    classifyError: (Throwable) -> String? = { null },
    call: suspend () -> Response<*>,
): Boolean {
    return try {
        val response = call()
        if (response.isSuccessful) {
            true
        } else {
            onError(parseApiErrorMessage(response) ?: fallbackMessage)
            false
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        onError(classifyError(e) ?: e.message ?: fallbackMessage)
        false
    }
}
