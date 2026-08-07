package com.bedrud.app.core.chat

/** Constants and helpers for preparing a picked chat image for multipart upload. */
object ChatUpload {
    /** Multipart form-data field name the server's chat-upload endpoint expects. */
    const val MULTIPART_FILE_FIELD = "file"

    /** MIME assumed when the content resolver can't determine the picked image's type. */
    const val DEFAULT_MIME = "image/jpeg"

    /** Maps an image MIME type to the file extension used in the uploaded filename. */
    fun extensionForMime(mime: String): String = when (mime) {
        "image/png" -> "png"
        "image/gif" -> "gif"
        "image/webp" -> "webp"
        else -> "jpg"
    }

    /** Builds the uploaded filename for the given [extension] (e.g. `upload.jpg`). */
    fun fileName(extension: String): String = "upload.$extension"
}
