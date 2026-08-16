package com.bedrud.app.core.chat

import android.content.ContentResolver
import android.net.Uri
import com.bedrud.app.core.api.RoomApi
import com.bedrud.app.core.livekit.ChatAttachment
import com.bedrud.app.core.meeting.chat.ChatWire
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/** Why a picked image never made it into a message. */
sealed interface ChatUploadFailure {
    /** The picked image could not be opened — revoked permission, or a provider that went away. */
    data object Unreadable : ChatUploadFailure

    /** The server refused it, most often because it is over the size the room allows. */
    data class Rejected(val code: Int) : ChatUploadFailure

    /** The request never completed: no route to the server, or it dropped the connection. */
    data object Unreachable : ChatUploadFailure
}

sealed interface ChatUploadResult {
    data class Success(val attachment: ChatAttachment) : ChatUploadResult
    data class Failure(val reason: ChatUploadFailure) : ChatUploadResult
}

/**
 * Puts a picked image on the server and returns the attachment that refers to it.
 *
 * Images do not travel over the data channel — only a URL to them does — so sending one is a
 * two-step affair: upload, then send an ordinary message carrying the result. This owns the first
 * step so the chat panel is left holding a single suspend call instead of the content resolver, the
 * multipart body and the response codes.
 *
 * Failures come back described rather than worded: the caller picks the string, because only it
 * knows which locale the person is reading.
 */
class ChatImageUploader(
    private val contentResolver: ContentResolver,
    private val roomApi: RoomApi,
) {
    suspend fun upload(roomId: String, uri: Uri): ChatUploadResult = withContext(Dispatchers.IO) {
        val bytes = try {
            contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (_: Exception) {
            null
        } ?: return@withContext ChatUploadResult.Failure(ChatUploadFailure.Unreadable)

        val mime = contentResolver.getType(uri) ?: ChatUpload.DEFAULT_MIME
        val part = MultipartBody.Part.createFormData(
            ChatUpload.MULTIPART_FILE_FIELD,
            ChatUpload.fileName(ChatUpload.extensionForMime(mime)),
            bytes.toRequestBody(mime.toMediaTypeOrNull()),
        )

        try {
            val response = roomApi.uploadChatImage(roomId, part)
            val body = response.body()
            if (!response.isSuccessful || body == null) {
                return@withContext ChatUploadResult.Failure(
                    ChatUploadFailure.Rejected(response.code())
                )
            }
            ChatUploadResult.Success(
                ChatAttachment(
                    kind = ChatWire.ATTACHMENT_KIND_IMAGE,
                    url = body.url,
                    mime = body.mime,
                    w = body.width,
                    h = body.height,
                    size = body.size,
                )
            )
        } catch (_: Exception) {
            ChatUploadResult.Failure(ChatUploadFailure.Unreachable)
        }
    }
}
