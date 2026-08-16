package com.bedrud.app.core.meeting.chat

import com.bedrud.app.core.livekit.ChatAttachment
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wire format is shared with the other clients, so these tests spell the JSON out by hand
 * rather than going through the encoder. A test that only round-trips this app's own output would
 * still pass if both ends of it drifted away from the agreed protocol.
 */
class ChatWireTest {

    private fun packet(json: String): ByteArray = json.toByteArray(Charsets.UTF_8)

    private fun completeOrNull(raw: ByteArray, topic: String? = ChatWire.CHAT_DATA_TOPIC) =
        (ChatWire.parse(raw, topic) as? ChatWire.Inbound.Complete)?.chat

    // ── What counts as a message ──────────────────────────────────────────────

    @Test
    fun `parses a plain message`() {
        val chat = completeOrNull(
            packet(
                """
                {"type":"chat","id":"m1","timestamp":1700000000000,
                 "senderName":"Sara","senderIdentity":"u-7","message":"hello","attachments":[]}
                """.trimIndent()
            )
        )

        assertEquals("m1", chat?.id)
        assertEquals("Sara", chat?.senderName)
        assertEquals("u-7", chat?.senderIdentity)
        assertEquals(1700000000000L, chat?.timestamp)
        assertEquals("hello", chat?.text)
    }

    @Test
    fun `ignores a reaction on the chat topic`() {
        // Reactions ride the same topic. Treating them as messages is what used to leave a sender's
        // name in the list with nothing under it.
        val raw = packet("""{"type":"reaction","messageId":"m1","emoji":"👍","voterIdentity":"u-7"}""")

        assertNull(ChatWire.parse(raw, ChatWire.CHAT_DATA_TOPIC))
    }

    @Test
    fun `ignores a poll, which has no text to draw`() {
        val raw = packet(
            """
            {"type":"chat","id":"m2","timestamp":1,"senderName":"Sara","senderIdentity":"u-7",
             "message":"","attachments":[],"poll":{"question":"Lunch?","options":["a","b"]}}
            """.trimIndent()
        )

        assertNull(ChatWire.parse(raw, ChatWire.CHAT_DATA_TOPIC))
    }

    @Test
    fun `ignores an empty message`() {
        val raw = packet("""{"type":"chat","id":"m3","senderName":"Sara","message":"","attachments":[]}""")

        assertNull(ChatWire.parse(raw, ChatWire.CHAT_DATA_TOPIC))
    }

    @Test
    fun `ignores malformed bytes`() {
        assertNull(ChatWire.parse(packet("not json at all"), ChatWire.CHAT_DATA_TOPIC))
    }

    @Test
    fun `reads a typeless payload on the chat topic, for older clients`() {
        val raw = packet("""{"senderName":"Sara","message":"from an old build"}""")

        assertEquals("from an old build", completeOrNull(raw)?.text)
    }

    @Test
    fun `ignores a typeless payload that arrived on another topic`() {
        val raw = packet("""{"senderName":"Sara","message":"presence, not chat"}""")

        assertNull(ChatWire.parse(raw, topic = "presence"))
    }

    @Test
    fun `falls back to arrival time when a message carries no timestamp`() {
        val before = System.currentTimeMillis()
        val chat = completeOrNull(packet("""{"type":"chat","id":"m4","message":"hi"}"""))

        assertTrue((chat?.timestamp ?: 0L) >= before)
    }

    // ── Attachments ───────────────────────────────────────────────────────────

    @Test
    fun `round-trips an image attachment`() {
        val attachment = ChatAttachment(
            kind = ChatWire.ATTACHMENT_KIND_IMAGE,
            url = "/uploads/chat/room-1/abc.jpg",
            mime = "image/jpeg",
            w = 1200,
            h = 800,
            size = 40_000,
        )
        val packets = ChatWire.encodeChat(
            senderName = "Sara",
            senderIdentity = "u-7",
            text = "look",
            attachments = listOf(attachment),
        )

        assertEquals(1, packets.size)
        assertEquals(listOf(attachment), completeOrNull(packets.single())?.attachments)
    }

    // ── Splitting oversized messages ──────────────────────────────────────────

    @Test
    fun `sends a short message as a single packet`() {
        val packets = ChatWire.encodeChat("Sara", "u-7", "hello", emptyList())

        assertEquals(1, packets.size)
        assertEquals("chat", JSONObject(String(packets.single())).getString("type"))
    }

    @Test
    fun `splits a message that would not fit, and every packet stays under the limit`() {
        val text = "x".repeat(150_000)
        val packets = ChatWire.encodeChat("Sara", "u-7", text, emptyList())

        assertTrue("expected a header plus parts", packets.size > 1)
        assertEquals("chat_chunk_meta", JSONObject(String(packets.first())).getString("type"))
        packets.forEach {
            assertTrue("packet of ${it.size} bytes exceeds the safe size", it.size <= ChatWire.SafePayloadBytes)
        }
    }

    @Test
    fun `announces a part count that matches the parts actually sent`() {
        val packets = ChatWire.encodeChat("Sara", "u-7", "x".repeat(150_000), emptyList())
        val meta = JSONObject(String(packets.first()))
        val messageParts = packets.drop(1).count {
            JSONObject(String(it)).getString("kind") == "message"
        }

        assertEquals(meta.getInt("messageChunks"), messageParts)
        assertEquals(0, meta.getInt("pollChunks"))
    }

    @Test
    fun `never cuts an emoji in half`() {
        // Each emoji is a surrogate pair. A cut between the halves would not survive UTF-8, so the
        // reassembled text would come back with replacement characters where the split landed.
        val text = "🎉".repeat(40_000)
        val packets = ChatWire.encodeChat("Sara", "u-7", text, emptyList())
        val rebuilt = packets.drop(1)
            .map { JSONObject(String(it, Charsets.UTF_8)) }
            .filter { it.getString("kind") == "message" }
            .sortedBy { it.getInt("index") }
            .joinToString("") { it.getString("part") }

        assertEquals(text, rebuilt)
        assertTrue("a surrogate pair was split", packets.drop(1).none { String(it).contains('�') })
    }
}
