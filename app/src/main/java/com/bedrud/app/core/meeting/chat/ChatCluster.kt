package com.bedrud.app.core.meeting.chat

import com.bedrud.app.core.livekit.ChatMessage

/**
 * A run of messages from one person, sent close enough together to read as a single turn.
 *
 * The panel draws a cluster once — one name, one avatar — instead of repeating them above every
 * line. Someone typing four short sentences then reads as four sentences, not as four strangers.
 */
data class ChatCluster(
    /** The first message's id, so the list can key on something stable as the run grows. */
    val id: String,
    val senderName: String,
    val senderIdentity: String,
    val isLocal: Boolean,
    val messages: List<ChatMessage>,
)

/**
 * How long a pause has to be before the next message starts a new turn rather than continuing the
 * last one. Long enough to cover someone thinking mid-sentence, short enough that a reply half an
 * hour later is not glued onto what came before.
 */
const val ChatClusterGapMs = 5 * 60_000L

/**
 * Groups messages into [ChatCluster]s, breaking the run whenever the sender changes or the pause
 * between two messages exceeds [gapMs].
 *
 * Senders are told apart by identity rather than by display name, since two people in a room may
 * well pick the same name. Messages that arrived without one — from a client older than the typed
 * protocol — fall back to the name, which is the only thing that distinguishes them.
 */
fun List<ChatMessage>.clustered(gapMs: Long = ChatClusterGapMs): List<ChatCluster> {
    if (isEmpty()) return emptyList()

    // Ordered by when each message was sent rather than by when this device happened to receive it:
    // the data channel can deliver a burst out of order, and a conversation that reads 7, 8, 6 is
    // wrong however it arrived. Stable, so messages sharing a timestamp keep their arrival order.
    val ordered = sortedBy { it.timestamp }

    val clusters = mutableListOf<ChatCluster>()
    var current = mutableListOf<ChatMessage>()

    fun flush() {
        val first = current.firstOrNull() ?: return
        clusters += ChatCluster(
            id = first.id,
            senderName = first.senderName,
            senderIdentity = first.senderIdentity,
            isLocal = first.isLocal,
            messages = current.toList(),
        )
        current = mutableListOf()
    }

    ordered.forEach { message ->
        val previous = current.lastOrNull()
        // Never negative: the sort above guarantees each message is at least as late as the last.
        val pause = previous?.let { message.timestamp - it.timestamp } ?: 0L
        val continues = previous != null &&
            message.isLocal == previous.isLocal &&
            message.senderKey() == previous.senderKey() &&
            pause <= gapMs
        if (!continues) flush()
        current += message
    }
    flush()

    return clusters
}

private fun ChatMessage.senderKey(): String = senderIdentity.ifBlank { senderName }

/**
 * One message as the list draws it: the message itself, who sent it, and where it sits in their run.
 *
 * Clusters describe the grouping, but they cannot *be* the list items — a run of forty messages
 * would then be a single item, so the list could neither drop what has scrolled away nor scroll
 * precisely to the newest line. Flattening keeps one item per message and carries the run's shape
 * along on each of them.
 */
data class ChatRow(
    val message: ChatMessage,
    val senderName: String,
    val senderIdentity: String,
    val isLocal: Boolean,
    val startsRun: Boolean,
    val endsRun: Boolean,
)

fun List<ChatCluster>.rows(): List<ChatRow> = flatMap { cluster ->
    cluster.messages.mapIndexed { index, message ->
        ChatRow(
            message = message,
            senderName = cluster.senderName,
            senderIdentity = cluster.senderIdentity,
            isLocal = cluster.isLocal,
            startsRun = index == 0,
            endsRun = index == cluster.messages.lastIndex,
        )
    }
}
