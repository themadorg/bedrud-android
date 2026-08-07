package com.bedrud.app.core.meeting.chat

import com.bedrud.app.core.livekit.ChatAttachment
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/**
 * Encodes/decodes the chat data-channel payloads, mirroring [com.bedrud.app.core.meeting.stage.StageWire]
 * so every wire format lives in one typed object instead of inline JSON in RoomManager.
 */
object ChatWire {
    const val CHAT_DATA_TOPIC = "chat"

    /** Attachment kind for image payloads, shared with the meeting UI that sends/filters them. */
    const val ATTACHMENT_KIND_IMAGE = "image"

    // Wire message type carried in the payload's "type" field.
    private const val TYPE_CHAT = "chat"

    /**
     * A decoded incoming chat payload. [senderName] may be blank — the caller falls back to the
     * LiveKit participant that delivered the packet.
     */
    data class IncomingChat(
        val senderName: String,
        val text: String,
        val attachments: List<ChatAttachment>,
    )

    fun encodeChat(
        senderName: String,
        senderIdentity: String,
        text: String,
        attachments: List<ChatAttachment>,
    ): ByteArray =
        JSONObject().apply {
            put("type", TYPE_CHAT)
            put("id", UUID.randomUUID().toString())
            put("timestamp", System.currentTimeMillis())
            put("message", text)
            put("senderName", senderName)
            put("senderIdentity", senderIdentity)
            if (attachments.isNotEmpty()) {
                val attArray = JSONArray()
                attachments.forEach { attArray.put(it.toJson()) }
                put("attachments", attArray)
            }
        }.toString().toByteArray(Charsets.UTF_8)

    /**
     * Parses raw data-channel bytes as a chat message — recognized by [topic] or by the payload's
     * own type field (old clients sent no topic). Null when the payload is not chat or malformed.
     */
    fun parseChat(raw: ByteArray, topic: String?): IncomingChat? {
        return try {
            val json = JSONObject(String(raw, Charsets.UTF_8))
            val isChat = topic == CHAT_DATA_TOPIC || json.optString("type") == TYPE_CHAT
            if (!isChat) return null
            IncomingChat(
                senderName = json.optString("senderName", ""),
                text = json.optString("message", ""),
                attachments = parseAttachments(json.optJSONArray("attachments")),
            )
        } catch (_: Exception) {
            null
        }
    }

    // Forward-compatible: old clients send no attachments array.
    private fun parseAttachments(array: JSONArray?): List<ChatAttachment> {
        array ?: return emptyList()
        val attachments = mutableListOf<ChatAttachment>()
        for (i in 0 until array.length()) {
            val att = array.optJSONObject(i) ?: continue
            attachments.add(
                ChatAttachment(
                    kind = att.optString("kind", ATTACHMENT_KIND_IMAGE),
                    url = att.optString("url", ""),
                    mime = att.optString("mime", ""),
                    w = att.optInt("w", 0),
                    h = att.optInt("h", 0),
                    size = att.optInt("size", 0),
                )
            )
        }
        return attachments
    }

    private fun ChatAttachment.toJson(): JSONObject =
        JSONObject().apply {
            put("kind", kind)
            put("url", url)
            put("mime", mime)
            put("w", w)
            put("h", h)
            put("size", size)
        }
}
