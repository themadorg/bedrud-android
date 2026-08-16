package com.bedrud.app.core.livekit

import org.json.JSONObject

/**
 * The JSON blob every participant carries on the LiveKit session.
 *
 * Two parties write to it and neither owns it outright: the app puts the person's avatar there, and
 * the server sets moderation flags on it — which is how a chat block reaches a client at all, since
 * chat itself never passes through the server. Anything written back therefore has to be merged in
 * rather than assigned over, or one side silently erases the other's field.
 */
object ParticipantMetadata {
    private const val KEY_AVATAR_URL = "avatarUrl"
    private const val KEY_CHAT_BLOCKED = "chatBlocked"

    fun avatarUrl(metadata: String?): String? =
        parse(metadata)?.optString(KEY_AVATAR_URL)?.takeIf { it.isNotBlank() }

    /**
     * Whether a moderator has blocked this participant from chatting.
     *
     * Enforced here and not on the server: messages travel directly between participants, so the
     * server has nothing to intercept and can only ask each client to respect the flag.
     */
    fun isChatBlocked(metadata: String?): Boolean =
        parse(metadata)?.optBoolean(KEY_CHAT_BLOCKED, false) == true

    /** Returns [metadata] with the avatar set, leaving every other field as it was. */
    fun withAvatarUrl(metadata: String?, avatarUrl: String): String =
        (parse(metadata) ?: JSONObject()).put(KEY_AVATAR_URL, avatarUrl).toString()

    private fun parse(metadata: String?): JSONObject? {
        if (metadata.isNullOrBlank()) return null
        return try {
            JSONObject(metadata)
        } catch (_: Exception) {
            null
        }
    }
}
