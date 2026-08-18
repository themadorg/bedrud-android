package com.bedrud.app.core.meeting.chat

/**
 * Rebuilds the messages that arrived split across several data-channel packets.
 *
 * A sender that runs out of room in one packet publishes a header announcing how many parts each
 * section was cut into, then the parts themselves. This holds the parts of every message still
 * being delivered and hands back a whole [ChatWire.IncomingChat] the moment the last one lands.
 *
 * Half-delivered messages are dropped once they go quiet for [maxAgeMs] — a sender that leaves
 * mid-message never finishes, and the parts already received are of no use to anyone. Not
 * thread-safe: it belongs to the single event loop that reads the data channel.
 */
class ChatChunkAssembler(private val maxAgeMs: Long = DefaultMaxAgeMs) {

    private class Pending(
        val meta: ChatWire.Inbound.ChunkMeta,
        val sections: Map<String, Array<String?>>,
        var updatedAt: Long,
    ) {
        val isComplete: Boolean get() = sections.values.all { parts -> parts.all { it != null } }

        fun joined(section: String): String =
            sections[section]?.joinToString(separator = "") { it.orEmpty() }.orEmpty()
    }

    // Insertion-ordered so the oldest entry is the one evicted when the cap is reached.
    private val pending = LinkedHashMap<String, Pending>()

    /**
     * Feeds one raw packet in. Returns a message only when this packet completed it — a header, a
     * part that leaves gaps behind, or a payload that is not chat at all all return null.
     */
    fun accept(
        raw: ByteArray,
        topic: String?,
        now: Long = System.currentTimeMillis(),
    ): ChatWire.IncomingChat? {
        prune(now)
        return when (val inbound = ChatWire.parse(raw, topic)) {
            is ChatWire.Inbound.Complete -> inbound.chat
            is ChatWire.Inbound.ChunkMeta -> {
                begin(inbound, now)
                null
            }
            is ChatWire.Inbound.ChunkPart -> apply(inbound, now)
            null -> null
        }
    }

    /** Forgets every part held so far, for when the room is left and the message list is cleared. */
    fun clear() = pending.clear()

    private fun begin(meta: ChatWire.Inbound.ChunkMeta, now: Long) {
        if (meta.messageChunks > MaxChunksPerSection ||
            meta.attachmentChunks > MaxChunksPerSection ||
            meta.pollChunks > MaxChunksPerSection
        ) {
            return
        }
        // A repeated header for a message already in flight would otherwise throw away the parts
        // that have arrived since the first one.
        pending[meta.id]?.let { existing ->
            if (existing.meta == meta) {
                existing.updatedAt = now
                return
            }
        }
        if (pending.size >= MaxPendingMessages) {
            pending.keys.firstOrNull()?.let { pending.remove(it) }
        }
        pending[meta.id] = Pending(
            meta = meta,
            sections = mapOf(
                ChatWire.SECTION_MESSAGE to arrayOfNulls(meta.messageChunks),
                ChatWire.SECTION_ATTACHMENTS to arrayOfNulls(meta.attachmentChunks),
                ChatWire.SECTION_POLL to arrayOfNulls(meta.pollChunks),
            ),
            updatedAt = now,
        )
    }

    private fun apply(chunk: ChatWire.Inbound.ChunkPart, now: Long): ChatWire.IncomingChat? {
        // Parts that arrive without their header are unusable on their own. Delivery is reliable
        // and ordered, so this only happens once a message has been pruned or evicted.
        val entry = pending[chunk.id] ?: return null
        val parts = entry.sections[chunk.section] ?: return null
        if (chunk.index >= parts.size) return null
        if (parts[chunk.index] != null) return null
        parts[chunk.index] = chunk.part
        entry.updatedAt = now
        if (!entry.isComplete) return null

        // Dropped before assembling, so a duplicate of the final part cannot deliver it twice.
        pending.remove(chunk.id)
        return ChatWire.assembled(
            meta = entry.meta,
            message = entry.joined(ChatWire.SECTION_MESSAGE),
            attachmentsJson = entry.joined(ChatWire.SECTION_ATTACHMENTS),
        )
    }

    private fun prune(now: Long) {
        if (pending.isEmpty()) return
        val cutoff = now - maxAgeMs
        pending.entries.removeAll { it.value.updatedAt < cutoff }
    }

    companion object {
        /** How long a half-delivered message is kept waiting for the parts that never came. */
        const val DefaultMaxAgeMs = 120_000L

        /**
         * Ceilings on what a sender may announce, so a wrong or hostile header cannot make this
         * allocate without bound. Both sit far above any message a person could actually send.
         */
        private const val MaxChunksPerSection = 256
        private const val MaxPendingMessages = 32
    }
}
