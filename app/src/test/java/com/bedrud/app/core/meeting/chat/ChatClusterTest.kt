package com.bedrud.app.core.meeting.chat

import com.bedrud.app.core.livekit.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatClusterTest {

    private var nextId = 0

    private fun message(
        identity: String,
        text: String = "hi",
        at: Long = 0L,
        name: String = identity,
        isLocal: Boolean = false,
    ) = ChatMessage(
        id = "m${nextId++}",
        senderName = name,
        senderIdentity = identity,
        text = text,
        timestamp = at,
        isLocal = isLocal,
    )

    @Test
    fun `groups nothing when there is nothing`() {
        assertEquals(emptyList<ChatCluster>(), emptyList<ChatMessage>().clustered())
    }

    @Test
    fun `keeps one sender's run together`() {
        val clusters = listOf(
            message("u-1", "one", at = 0),
            message("u-1", "two", at = 1_000),
            message("u-1", "three", at = 2_000),
        ).clustered()

        assertEquals(1, clusters.size)
        assertEquals(listOf("one", "two", "three"), clusters.single().messages.map { it.text })
    }

    @Test
    fun `starts a new cluster when the sender changes`() {
        val clusters = listOf(
            message("u-1", "mine", at = 0),
            message("u-2", "theirs", at = 1_000),
        ).clustered()

        assertEquals(2, clusters.size)
        assertEquals("u-1", clusters[0].senderIdentity)
        assertEquals("u-2", clusters[1].senderIdentity)
    }

    @Test
    fun `starts a new cluster after a long pause`() {
        val clusters = listOf(
            message("u-1", "before", at = 0),
            message("u-1", "after", at = ChatClusterGapMs + 1),
        ).clustered()

        assertEquals(2, clusters.size)
    }

    @Test
    fun `keeps a run together right up to the gap`() {
        val clusters = listOf(
            message("u-1", "before", at = 0),
            message("u-1", "after", at = ChatClusterGapMs),
        ).clustered()

        assertEquals(1, clusters.size)
    }

    @Test
    fun `tells apart two people who chose the same name`() {
        val clusters = listOf(
            message("u-1", "hi", name = "Sara"),
            message("u-2", "hi", name = "Sara"),
        ).clustered()

        assertEquals(2, clusters.size)
    }

    @Test
    fun `falls back to the name when a client sent no identity`() {
        val clusters = listOf(
            message(identity = "", name = "Sara", text = "one"),
            message(identity = "", name = "Sara", text = "two"),
            message(identity = "", name = "Reza", text = "three"),
        ).clustered()

        assertEquals(2, clusters.size)
        assertEquals(2, clusters[0].messages.size)
        assertEquals("Reza", clusters[1].senderName)
    }

    @Test
    fun `never merges the local side into a remote run`() {
        val clusters = listOf(
            message("u-1", "theirs", isLocal = false),
            message("u-1", "mine", isLocal = true),
        ).clustered()

        assertEquals(2, clusters.size)
        assertTrue(clusters[1].isLocal)
    }

    @Test
    fun `keeps the arrival order of messages sharing a timestamp`() {
        // Ordering is by send time, but a burst can share a millisecond; the sort has to be stable
        // or those would shuffle on every recomposition.
        val clusters = listOf(
            message("u-1", "one", at = 500),
            message("u-1", "two", at = 500),
            message("u-1", "three", at = 500),
        ).clustered()

        assertEquals(listOf("one", "two", "three"), clusters.single().messages.map { it.text })
    }

    @Test
    fun `keys a cluster on its first message`() {
        val first = message("u-1", "one")
        val clusters = listOf(first, message("u-1", "two")).clustered()

        assertEquals(first.id, clusters.single().id)
    }

    @Test
    fun `orders by when a message was sent, not when it arrived`() {
        // A burst can reach the data channel out of order; reading 7, 8, 6 is wrong however it came.
        val clusters = listOf(
            message("u-1", "seventh", at = 700),
            message("u-1", "eighth", at = 800),
            message("u-1", "sixth", at = 600),
        ).clustered()

        assertEquals(listOf("sixth", "seventh", "eighth"), clusters.single().messages.map { it.text })
    }

    // ── Flattening a run into list rows ───────────────────────────────────────

    @Test
    fun `marks where each row sits in its run`() {
        val rows = listOf(
            message("u-1", "one"),
            message("u-1", "two"),
            message("u-1", "three"),
        ).clustered().rows()

        assertEquals(listOf(true, false, false), rows.map { it.startsRun })
        assertEquals(listOf(false, false, true), rows.map { it.endsRun })
    }

    @Test
    fun `marks a lone message as both opening and closing its run`() {
        val rows = listOf(message("u-1", "alone")).clustered().rows()

        assertTrue(rows.single().startsRun)
        assertTrue(rows.single().endsRun)
    }

    @Test
    fun `gives every message a row, in order, carrying its sender`() {
        val messages = listOf(
            message("u-1", "a", name = "Sara"),
            message("u-2", "b", name = "Reza"),
            message("u-2", "c", name = "Reza"),
        )
        val rows = messages.clustered().rows()

        assertEquals(messages, rows.map { it.message })
        assertEquals(listOf("Sara", "Reza", "Reza"), rows.map { it.senderName })
        assertEquals(listOf(true, true, false), rows.map { it.startsRun })
    }

    @Test
    fun `keeps every message exactly once`() {
        val messages = listOf(
            message("u-1", at = 0),
            message("u-2", at = 1),
            message("u-2", at = 2),
            message("u-1", at = ChatClusterGapMs * 3),
        )

        assertEquals(messages, messages.clustered().flatMap { it.messages })
    }
}
