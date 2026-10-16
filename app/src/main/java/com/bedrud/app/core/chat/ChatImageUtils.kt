package com.bedrud.app.core.chat

import android.content.Context
import coil.request.ImageRequest
import com.bedrud.app.core.api.ApiHeaders
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object ChatImageUtils {
    fun resolveChatImageUrl(serverURL: String, url: String): String {
        if (url.startsWith("data:") || url.startsWith("http://") || url.startsWith("https://")) {
            return url
        }
        val base = serverURL.trimEnd('/')
        return if (url.startsWith("/")) "$base$url" else "$base/$url"
    }

    /**
     * Whether a chat image at [url] is on the account's own server at [serverURL], reached the way
     * the app reaches it: same scheme, host and port. Only then may the account's token go with the
     * request. The address comes from whoever sent the message, and any other host, or the right one
     * over plain http, would be handed the token of everyone who looks at the picture.
     */
    fun isOnServer(serverURL: String, url: String): Boolean {
        val server = serverURL.toHttpUrlOrNull() ?: return false
        val image = resolveChatImageUrl(serverURL, url).toHttpUrlOrNull() ?: return false
        return image.scheme == server.scheme && image.host == server.host && image.port == server.port
    }

    fun imageRequest(
        context: Context,
        serverURL: String,
        url: String,
        accessToken: String?,
    ): ImageRequest {
        val resolved = resolveChatImageUrl(serverURL, url)
        val builder = ImageRequest.Builder(context).data(resolved)
        if (!accessToken.isNullOrBlank() && isOnServer(serverURL, url)) {
            builder.setHeader(ApiHeaders.AUTHORIZATION, ApiHeaders.bearer(accessToken))
        }
        return builder.build()
    }
}
