package com.bedrud.app.core.meeting.presence

import org.json.JSONObject

/**
 * Encodes/decodes the `presence` data-channel payloads, alongside
 * [com.bedrud.app.core.meeting.chat.ChatWire] so every wire format lives in one typed object
 * rather than as inline JSON in RoomManager.
 *
 * Presence carries what a participant is doing rather than what they said. It is announced twice
 * by every client — here, and as a field on the participant's metadata — because the two reach
 * different people: this message is immediate but only heard by those already in the room, while
 * the metadata is what somebody joining later reads. Whoever writes one must write the other.
 */
object PresenceWire {

    const val PRESENCE_DATA_TOPIC = "presence"

    // Wire message type carried in the payload's "type" field.
    private const val TYPE_DEAFEN_STATE = "deafen_state"

    private const val KEY_TYPE = "type"
    private const val KEY_IDENTITY = "identity"
    private const val KEY_DEAFENED = "deafened"

    /** Somebody announcing whether they can currently hear the room. */
    data class DeafenState(val identity: String, val deafened: Boolean)

    fun encodeDeafenState(identity: String, deafened: Boolean): ByteArray =
        JSONObject().apply {
            put(KEY_TYPE, TYPE_DEAFEN_STATE)
            put(KEY_IDENTITY, identity)
            put(KEY_DEAFENED, deafened)
        }.toString().toByteArray(Charsets.UTF_8)

    /**
     * Parses raw data-channel bytes as a deafen announcement. Null when [topic] is not presence,
     * when the payload is some other presence message, or when it is malformed.
     *
     * An announcement without an identity names nobody and is dropped: applying it would have to
     * guess whose state changed, and the guess is a badge on the wrong person.
     */
    fun parseDeafenState(raw: ByteArray, topic: String?): DeafenState? {
        if (topic != PRESENCE_DATA_TOPIC) return null
        return try {
            val json = JSONObject(String(raw, Charsets.UTF_8))
            if (json.optString(KEY_TYPE) != TYPE_DEAFEN_STATE) return null
            val identity = json.optString(KEY_IDENTITY).takeIf { it.isNotBlank() } ?: return null
            DeafenState(identity = identity, deafened = json.optBoolean(KEY_DEAFENED, false))
        } catch (_: Exception) {
            null
        }
    }
}
