package com.bedrud.app.core.chat

import android.content.ContentResolver
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.bedrud.app.core.api.ApiHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Why a received image never reached the gallery. */
sealed interface ChatSaveFailure {
    /** The image could not be read: the server refused it, or there was no route to it. */
    data object Unreachable : ChatSaveFailure

    /** The gallery refused the write — no permission on older versions, or no room. */
    data object Unwritable : ChatSaveFailure
}

sealed interface ChatSaveResult {
    data object Saved : ChatSaveResult
    data class Failure(val reason: ChatSaveFailure) : ChatSaveResult
}

/**
 * Puts a received chat image in the device gallery.
 *
 * It has to read both forms an attachment can take: this server answers an upload by inlining the
 * image in the message, while a plain URL has to be fetched back with the sender's own credentials.
 * [ChatImageDecoder] and [ChatImageUtils] already draw that line, so this follows it rather than
 * inventing a third path.
 *
 * The file lands in `Pictures/Bedrud` through `MediaStore`, which needs no permission at all from
 * Android 10 onward. On the one older version this app still supports it does, and the caller is
 * expected to have asked before getting here.
 */
class ChatImageSaver(
    private val contentResolver: ContentResolver,
    private val httpClient: OkHttpClient = SharedClient,
) {
    suspend fun save(
        url: String,
        serverURL: String,
        accessToken: String?,
    ): ChatSaveResult = withContext(Dispatchers.IO) {
        val image = read(url, serverURL, accessToken)
            ?: return@withContext ChatSaveResult.Failure(ChatSaveFailure.Unreachable)
        if (write(image)) ChatSaveResult.Saved
        else ChatSaveResult.Failure(ChatSaveFailure.Unwritable)
    }

    private class Image(val bytes: ByteArray, val mime: String)

    private fun read(url: String, serverURL: String, accessToken: String?): Image? {
        if (ChatImageDecoder.isInlineImage(url)) {
            val bytes = ChatImageDecoder.decodeInlineBytes(url) ?: return null
            return Image(bytes, ChatImageDecoder.inlineMimeType(url) ?: ChatUpload.DEFAULT_MIME)
        }
        val request = Request.Builder()
            .url(ChatImageUtils.resolveChatImageUrl(serverURL, url))
            .apply {
                if (!accessToken.isNullOrBlank()) {
                    header(ApiHeaders.AUTHORIZATION, ApiHeaders.bearer(accessToken))
                }
            }
            .build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                val body = response.body
                if (!response.isSuccessful || body == null) return null
                Image(body.bytes(), response.header("Content-Type") ?: ChatUpload.DEFAULT_MIME)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun write(image: Image): Boolean {
        // Normalised: a Content-Type may carry a charset, and MediaStore wants the bare media type.
        val mime = image.mime.substringBefore(';').trim().ifEmpty { ChatUpload.DEFAULT_MIME }
        val details = ContentValues().apply {
            put(
                MediaStore.Images.Media.DISPLAY_NAME,
                "$FileNamePrefix${System.currentTimeMillis()}.${ChatUpload.extensionForMime(mime)}",
            )
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$AlbumName")
                // Hidden from the gallery until the bytes are all there, so nothing ever shows a
                // half-written picture.
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val target = try {
            contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, details)
        } catch (_: Exception) {
            null
        } ?: return false

        return try {
            contentResolver.openOutputStream(target)?.use { it.write(image.bytes) } ?: return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentResolver.update(
                    target,
                    ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                    null,
                    null,
                )
            }
            true
        } catch (_: Exception) {
            // Leaves nothing half-written behind for the gallery to find.
            runCatching { contentResolver.delete(target, null, null) }
            false
        }
    }

    companion object {
        /** The album received pictures are filed under, inside the device's Pictures directory. */
        const val AlbumName = "Bedrud"

        private const val FileNamePrefix = "bedrud-"

        /** One client for every save, rather than a pool and its threads per opened picture. */
        private val SharedClient by lazy { OkHttpClient() }
    }
}
