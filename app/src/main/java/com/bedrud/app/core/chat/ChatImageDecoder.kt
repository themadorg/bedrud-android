package com.bedrud.app.core.chat

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * Reads an inlined `data:` image.
 *
 * The chat upload endpoint answers with the image inlined rather than with a path to it, and clients
 * older than that endpoint inlined their attachments too. The image loader has no fetcher for the
 * scheme, so these never reach the network stack and are decoded here instead.
 */
object ChatImageDecoder {
    private const val DataUriPrefix = "data:"
    private const val Base64Marker = ";base64,"

    fun isInlineImage(url: String): Boolean = url.startsWith(DataUriPrefix)

    /**
     * The media type declared in the URI's own header, e.g. `image/png`. Null when it is not an
     * inlined image or declares nothing.
     */
    fun inlineMimeType(url: String): String? {
        if (!isInlineImage(url)) return null
        val header = url.substringBefore(',', missingDelimiterValue = "")
        val declared = header.removePrefix(DataUriPrefix).substringBefore(';')
        return declared.takeIf { it.isNotBlank() }
    }

    /** The raw bytes carried by an inlined image, for writing to a file. */
    fun decodeInlineBytes(url: String): ByteArray? {
        if (!isInlineImage(url)) return null
        val marker = url.indexOf(Base64Marker)
        val encoded = if (marker >= 0) {
            url.substring(marker + Base64Marker.length)
        } else {
            // No explicit marker: fall back to everything past the header, which is what this
            // decoding did before it moved here.
            url.substringAfter(',', missingDelimiterValue = "")
        }
        if (encoded.isEmpty()) return null
        return runCatching { Base64.decode(encoded, Base64.DEFAULT) }.getOrNull()
    }

    /** Null for anything that is not a readable base64 `data:` image. */
    fun decodeInlineImage(url: String): ImageBitmap? {
        val bytes = decodeInlineBytes(url) ?: return null
        return runCatching {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }
}
