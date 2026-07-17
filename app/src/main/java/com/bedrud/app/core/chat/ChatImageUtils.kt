package com.bedrud.app.core.chat

import android.content.Context
import coil.request.ImageRequest

object ChatImageUtils {
    fun resolveChatImageUrl(serverURL: String, url: String): String {
        if (url.startsWith("data:") || url.startsWith("http://") || url.startsWith("https://")) {
            return url
        }
        val base = serverURL.trimEnd('/')
        return if (url.startsWith("/")) "$base$url" else "$base/$url"
    }

    fun imageRequest(
        context: Context,
        serverURL: String,
        url: String,
        accessToken: String?,
    ): ImageRequest {
        val resolved = resolveChatImageUrl(serverURL, url)
        val builder = ImageRequest.Builder(context).data(resolved)
        if (!accessToken.isNullOrBlank() && !url.startsWith("data:")) {
            builder.setHeader("Authorization", "Bearer $accessToken")
        }
        return builder.build()
    }
}