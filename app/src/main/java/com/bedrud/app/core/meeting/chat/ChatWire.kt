package com.bedrud.app.core.meeting.chat

import com.bedrud.app.core.livekit.ChatAttachment
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/**
 * Encodes and decodes the chat data-channel payloads, so every wire format lives in one typed
 * object instead of inline JSON in `RoomManager`.
 *
 * The channel carries more than plain messages. Reactions and poll votes travel on this same topic
 * as packets of their own, and anything past [SafePayloadBytes] is split into a header packet plus
 * a run of parts. A payload therefore has to be recognised by its own `type` field — trusting the
 * topic alone is what used to turn every reaction, poll and chunk into a chat message with no text
 * in it.
 *
 * Reassembling the split payloads is [ChatChunkAssembler]'s job; this object only decides what a
 * single packet is and how to build one.
 */
object ChatWire {
    const val CHAT_DATA_TOPIC = "chat"

    /** Attachment kind for image payloads, shared with the meeting UI that sends/filters them. */
    const val ATTACHMENT_KIND_IMAGE = "image"

    /**
     * The data channel's hard payload ceiling, and the size a sender is allowed to reach before it
     * has to split. The gap between them is headroom for encoder and broker overhead; the other
     * clients split at the same point, so both ends agree on what needs chunking.
     */
    const val MaxPayloadBytes = 65_535
    const val SafePayloadBytes = 60_000

    // Wire message types carried in the payload's "type" field.
    private const val TYPE_CHAT = "chat"
    private const val TYPE_CHUNK_META = "chat_chunk_meta"
    private const val TYPE_CHUNK = "chat_chunk"
    private const val TYPE_REACTION = "reaction"
    private const val TYPE_POLL_VOTE = "poll_vote"

    // Which part of a split payload a chunk belongs to.
    internal const val SECTION_MESSAGE = "message"
    internal const val SECTION_ATTACHMENTS = "attachments"
    internal const val SECTION_POLL = "poll"

    /**
     * A decoded chat message. [senderName] may be blank — the caller falls back to the LiveKit
     * participant that delivered the packet.
     */
    data class IncomingChat(
        val id: String,
        val senderName: String,
        val senderIdentity: String,
        val timestamp: Long,
        val text: String,
        val attachments: List<ChatAttachment>,
        val poll: ChatPoll? = null,
    )

    /** What a single packet turned out to be. */
    sealed interface Inbound {
        /** A whole message that arrived in one packet. */
        data class Complete(val chat: IncomingChat) : Inbound

        /**
         * Someone's reaction to a message, which may equally be them taking it back — the packet
         * says only what was tapped, and [ChatReactions.toggled] decides which of the two it meant.
         */
        data class Reaction(
            val messageId: String,
            val voterIdentity: String,
            val emoji: String,
        ) : Inbound

        /** Someone's vote in a poll, replacing whatever they voted for before. */
        data class PollVote(
            val messageId: String,
            val voterIdentity: String,
            val optionId: String,
        ) : Inbound

        /** The header of a split payload, announcing how many parts each section was cut into. */
        data class ChunkMeta(
            val id: String,
            val senderName: String,
            val senderIdentity: String,
            val timestamp: Long,
            val messageChunks: Int,
            val attachmentChunks: Int,
            val pollChunks: Int,
        ) : Inbound

        /** One part of a split payload. */
        data class ChunkPart(
            val id: String,
            val section: String,
            val index: Int,
            val part: String,
        ) : Inbound
    }

    /**
     * Builds the packets for one outgoing message: a single payload when it fits, otherwise a
     * header followed by the parts of each oversized section. Publish them in order — the receiving
     * assembler needs the header first.
     */
    fun encodeChat(
        senderName: String,
        senderIdentity: String,
        text: String,
        attachments: List<ChatAttachment>,
        poll: ChatPoll? = null,
        id: String = UUID.randomUUID().toString(),
        timestamp: Long = System.currentTimeMillis(),
    ): List<ByteArray> {
        val attachmentsJson = encodeAttachments(attachments)
        val pollJson = poll?.let { encodePoll(it).toString() }.orEmpty()
        val single =
            chatPacket(id, senderName, senderIdentity, timestamp, text, attachmentsJson, pollJson)
        if (single.size <= SafePayloadBytes) return listOf(single)

        val messageParts = splitByEncodedSize(text) { chunkPacket(id, SECTION_MESSAGE, 0, it) }
        val attachmentParts =
            splitByEncodedSize(attachmentsJson) { chunkPacket(id, SECTION_ATTACHMENTS, 0, it) }
        val pollParts = splitByEncodedSize(pollJson) { chunkPacket(id, SECTION_POLL, 0, it) }

        val meta = metaPacket(
            id = id,
            senderName = senderName,
            senderIdentity = senderIdentity,
            timestamp = timestamp,
            messageChunks = messageParts.size,
            attachmentChunks = attachmentParts.size,
            pollChunks = pollParts.size,
        )
        require(meta.size <= SafePayloadBytes) { "Chat header exceeds the data channel limit" }

        return buildList {
            add(meta)
            messageParts.forEachIndexed { index, part ->
                add(chunkPacket(id, SECTION_MESSAGE, index, part))
            }
            attachmentParts.forEachIndexed { index, part ->
                add(chunkPacket(id, SECTION_ATTACHMENTS, index, part))
            }
            pollParts.forEachIndexed { index, part ->
                add(chunkPacket(id, SECTION_POLL, index, part))
            }
        }
    }

    /**
     * One person's reaction to a message. Small enough that it never needs splitting, and sent on
     * its own rather than as a new version of the message: the message may well predate this
     * device, and nobody may rewrite what somebody else said.
     */
    fun encodeReaction(messageId: String, voterIdentity: String, emoji: String): ByteArray =
        JSONObject().apply {
            put("type", TYPE_REACTION)
            put("messageId", messageId)
            put("voterIdentity", voterIdentity)
            put("emoji", emoji)
        }.toByteArray()

    /** One person's vote, carrying the option they chose rather than the tally they arrived at. */
    fun encodePollVote(messageId: String, voterIdentity: String, optionId: String): ByteArray =
        JSONObject().apply {
            put("type", TYPE_POLL_VOTE)
            put("messageId", messageId)
            put("voterIdentity", voterIdentity)
            put("optionId", optionId)
        }.toByteArray()

    /**
     * Reads one raw packet. Null when it is not part of the chat protocol, or when it is a message
     * this app has nothing to draw for — one with no text, no picture and no poll in it. Dropping
     * those beats listing a sender's name above an empty space.
     */
    fun parse(raw: ByteArray, topic: String?): Inbound? {
        val json = try {
            JSONObject(String(raw, Charsets.UTF_8))
        } catch (_: Exception) {
            return null
        }
        // Clients older than the typed protocol sent a bare chat payload with no type field, so on
        // the chat topic an absent type still means a plain message.
        val type = json.optString("type").ifEmpty {
            if (topic == CHAT_DATA_TOPIC) TYPE_CHAT else ""
        }
        return when (type) {
            TYPE_CHAT -> parseComplete(json)
            TYPE_CHUNK_META -> parseChunkMeta(json)
            TYPE_CHUNK -> parseChunkPart(json)
            TYPE_REACTION -> parseReaction(json)
            TYPE_POLL_VOTE -> parsePollVote(json)
            else -> null
        }
    }

    /**
     * Assembles the decoded sections of a split payload, applying the same emptiness rule a
     * single-packet message gets.
     */
    internal fun assembled(
        meta: Inbound.ChunkMeta,
        message: String,
        attachmentsJson: String,
        pollJson: String,
    ): IncomingChat? {
        val attachments = parseAttachments(
            try {
                if (attachmentsJson.isEmpty()) null else JSONArray(attachmentsJson)
            } catch (_: Exception) {
                null
            }
        )
        val poll = parsePoll(
            try {
                if (pollJson.isEmpty()) null else JSONObject(pollJson)
            } catch (_: Exception) {
                null
            }
        )
        if (message.isEmpty() && attachments.isEmpty() && poll == null) return null
        return IncomingChat(
            id = meta.id,
            senderName = meta.senderName,
            senderIdentity = meta.senderIdentity,
            timestamp = meta.timestamp,
            text = message,
            attachments = attachments,
            poll = poll,
        )
    }

    private fun parseComplete(json: JSONObject): Inbound? {
        val text = json.optString("message", "")
        val attachments = parseAttachments(json.optJSONArray("attachments"))
        val poll = parsePoll(json.optJSONObject("poll"))
        if (text.isEmpty() && attachments.isEmpty() && poll == null) return null
        return Inbound.Complete(
            IncomingChat(
                id = json.optString("id", ""),
                senderName = json.optString("senderName", ""),
                senderIdentity = json.optString("senderIdentity", ""),
                timestamp = json.optTimestamp(),
                text = text,
                attachments = attachments,
                poll = poll,
            )
        )
    }

    private fun parseReaction(json: JSONObject): Inbound? {
        val messageId = json.optString("messageId", "")
        val voterIdentity = json.optString("voterIdentity", "")
        val emoji = json.optString("emoji", "")
        if (messageId.isEmpty() || voterIdentity.isEmpty()) return null
        // Checked here rather than at the chip row, so whatever a remote client considers an emoji
        // cannot reach the UI as a line of text wearing a chip.
        if (!isReactionEmoji(emoji)) return null
        return Inbound.Reaction(messageId = messageId, voterIdentity = voterIdentity, emoji = emoji)
    }

    private fun parsePollVote(json: JSONObject): Inbound? {
        val messageId = json.optString("messageId", "")
        val voterIdentity = json.optString("voterIdentity", "")
        val optionId = json.optString("optionId", "")
        if (messageId.isEmpty() || voterIdentity.isEmpty() || optionId.isEmpty()) return null
        return Inbound.PollVote(
            messageId = messageId,
            voterIdentity = voterIdentity,
            optionId = optionId,
        )
    }

    private fun parseChunkMeta(json: JSONObject): Inbound? {
        val id = json.optString("id", "")
        if (id.isEmpty()) return null
        val messageChunks = json.optInt("messageChunks", 0)
        val attachmentChunks = json.optInt("attachmentChunks", 0)
        val pollChunks = json.optInt("pollChunks", 0)
        if (messageChunks < 0 || attachmentChunks < 0 || pollChunks < 0) return null
        if (messageChunks + attachmentChunks + pollChunks == 0) return null
        return Inbound.ChunkMeta(
            id = id,
            senderName = json.optString("senderName", ""),
            senderIdentity = json.optString("senderIdentity", ""),
            timestamp = json.optTimestamp(),
            messageChunks = messageChunks,
            attachmentChunks = attachmentChunks,
            pollChunks = pollChunks,
        )
    }

    private fun parseChunkPart(json: JSONObject): Inbound? {
        val id = json.optString("id", "")
        val section = json.optString("kind", "")
        val index = json.optInt("index", -1)
        if (id.isEmpty() || index < 0) return null
        if (section != SECTION_MESSAGE && section != SECTION_ATTACHMENTS && section != SECTION_POLL) {
            return null
        }
        return Inbound.ChunkPart(
            id = id,
            section = section,
            index = index,
            part = json.optString("part", ""),
        )
    }

    /** Absent or unparseable timestamps fall back to arrival time, which still orders correctly. */
    private fun JSONObject.optTimestamp(): Long {
        val value = optLong("timestamp", 0L)
        return if (value > 0L) value else System.currentTimeMillis()
    }

    /**
     * Cuts [text] into the longest pieces whose encoded packet still fits, by binary search on the
     * built packet rather than on the text — the JSON envelope and any escaping count against the
     * limit too.
     */
    private fun splitByEncodedSize(text: String, build: (String) -> ByteArray): List<String> {
        if (text.isEmpty()) return emptyList()
        val parts = mutableListOf<String>()
        var remaining = text
        while (remaining.isNotEmpty()) {
            var low = 1
            var high = remaining.length
            var best = 0
            while (low <= high) {
                val mid = (low + high) / 2
                if (build(remaining.substring(0, mid)).size <= SafePayloadBytes) {
                    best = mid
                    low = mid + 1
                } else {
                    high = mid - 1
                }
            }
            // Never cut between the halves of a surrogate pair: a lone surrogate does not survive
            // UTF-8 encoding, so an emoji split across two packets would come back as a question
            // mark. One character short is always a legal cut.
            if (best in 1 until remaining.length && remaining[best - 1].isHighSurrogate()) best--
            require(best > 0) { "Chat chunk overhead exceeds the data channel limit" }
            parts.add(remaining.substring(0, best))
            remaining = remaining.substring(best)
        }
        return parts
    }

    private fun chatPacket(
        id: String,
        senderName: String,
        senderIdentity: String,
        timestamp: Long,
        text: String,
        attachmentsJson: String,
        pollJson: String,
    ): ByteArray =
        JSONObject().apply {
            put("type", TYPE_CHAT)
            put("id", id)
            put("timestamp", timestamp)
            put("message", text)
            put("senderName", senderName)
            put("senderIdentity", senderIdentity)
            if (attachmentsJson.isNotEmpty()) put("attachments", JSONArray(attachmentsJson))
            if (pollJson.isNotEmpty()) put("poll", JSONObject(pollJson))
        }.toByteArray()

    private fun metaPacket(
        id: String,
        senderName: String,
        senderIdentity: String,
        timestamp: Long,
        messageChunks: Int,
        attachmentChunks: Int,
        pollChunks: Int,
    ): ByteArray =
        JSONObject().apply {
            put("type", TYPE_CHUNK_META)
            put("id", id)
            put("timestamp", timestamp)
            put("senderName", senderName)
            put("senderIdentity", senderIdentity)
            put("messageChunks", messageChunks)
            put("attachmentChunks", attachmentChunks)
            put("pollChunks", pollChunks)
        }.toByteArray()

    private fun chunkPacket(id: String, section: String, index: Int, part: String): ByteArray =
        JSONObject().apply {
            put("type", TYPE_CHUNK)
            put("id", id)
            put("kind", section)
            put("index", index)
            put("part", part)
        }.toByteArray()

    private fun encodeAttachments(attachments: List<ChatAttachment>): String {
        if (attachments.isEmpty()) return ""
        val array = JSONArray()
        attachments.forEach { array.put(it.toJson()) }
        return array.toString()
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

    private fun encodePoll(poll: ChatPoll): JSONObject =
        JSONObject().apply {
            put("id", poll.id)
            put("question", poll.question)
            put("options", JSONArray().apply {
                poll.options.forEach { option ->
                    put(JSONObject().apply {
                        put("id", option.id)
                        put("text", option.text)
                    })
                }
            })
            // Always written, even when empty: a poll arriving without the field would read as
            // malformed to a client that expects the tally to be part of the poll.
            put("votes", JSONObject().apply { poll.votes.forEach { (voter, optionId) -> put(voter, optionId) } })
        }

    /**
     * Reads a poll, or null when what arrived could not be drawn as one — no question, or fewer
     * than [MinPollOptions] answers to choose between. Votes for options the poll does not list are
     * dropped rather than kept as a tally nobody can see the target of.
     */
    private fun parsePoll(json: JSONObject?): ChatPoll? {
        json ?: return null
        val id = json.optString("id", "")
        val question = json.optString("question", "")
        if (id.isEmpty() || question.isEmpty()) return null

        val optionsArray = json.optJSONArray("options") ?: return null
        val options = mutableListOf<ChatPollOption>()
        for (i in 0 until optionsArray.length()) {
            val row = optionsArray.optJSONObject(i) ?: continue
            val optionId = row.optString("id", "")
            val text = row.optString("text", "")
            if (optionId.isNotEmpty() && text.isNotEmpty()) {
                options.add(ChatPollOption(id = optionId, text = text))
            }
        }
        if (options.size < MinPollOptions) return null

        val votes = mutableMapOf<String, String>()
        json.optJSONObject("votes")?.let { voteJson ->
            voteJson.keys().forEach { voter ->
                val optionId = voteJson.optString(voter, "")
                if (options.any { it.id == optionId }) votes[voter] = optionId
            }
        }

        return ChatPoll(id = id, question = question, options = options, votes = votes)
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

    private fun JSONObject.toByteArray(): ByteArray = toString().toByteArray(Charsets.UTF_8)
}
