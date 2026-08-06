package com.bedrud.app.core

import android.content.Context
import com.bedrud.app.R
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Maps a low-level failure to a localized, user-facing message by its *type* — the raw exception
 * text (English, technical) is never shown. Falls back to a generic message so every path yields a
 * translated string.
 */
fun Throwable.toUserMessage(context: Context): String {
    val resId = when (this) {
        is UnknownHostException -> R.string.error_network_unavailable
        is SocketTimeoutException, is InterruptedIOException -> R.string.error_timeout
        is ConnectException, is SSLException -> R.string.error_server_unreachable
        else -> R.string.error_generic
    }
    return context.getString(resId)
}
