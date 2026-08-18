package com.bedrud.app.core.meeting.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatChunkAssemblerTest {

    private val topic = ChatWire.CHAT_DATA_TOPIC

    private fun packet(json: String): ByteArray = json.toByteArray(Charsets.UTF_8)

    private fun meta(id: String, messageChunks: Int, attachmentChunks: Int = 0) = packet(
        """
        {"type":"chat_chunk_meta","id":"$id","timestamp":1700000000000,
         "senderName":"Sara","senderIdentity":"u-7",
         "messageChunks":$messageChunks,"attachmentChunks":$attachmentChunks,"pollChunks":0}
        """.trimIndent()
    )

    private fun chunk(id: String, index: Int, part: String, section: String = "message") =
        packet("""{"type":"chat_chunk","id":"$id","kind":"$section","index":$index,"part":"$part"}""")

    /** Feeds this app's own packets back in, which is what the other end has to be able to do. */
    private fun assembleOwnOutput(text: String): ChatWire.IncomingChat? {
        val assembler = ChatChunkAssembler()
        return ChatWire.encodeChat("Sara", "u-7", text, emptyList())
            .map { assembler.accept(it, topic) }
            .lastOrNull { it != null }
    }

    @Test
    fun `passes a single-packet message straight through`() {
        val assembler = ChatChunkAssembler()
        val raw = packet("""{"type":"chat","id":"m1","senderName":"Sara","message":"hi"}""")

        assertEquals("hi", assembler.accept(raw, topic)?.text)
    }

    @Test
    fun `holds a split message until its last part lands`() {
        val assembler = ChatChunkAssembler()

        assertNull(assembler.accept(meta("m1", messageChunks = 3), topic))
        assertNull(assembler.accept(chunk("m1", 0, "one "), topic))
        assertNull(assembler.accept(chunk("m1", 1, "two "), topic))
        val done = assembler.accept(chunk("m1", 2, "three"), topic)

        assertEquals("one two three", done?.text)
        assertEquals("Sara", done?.senderName)
        assertEquals("u-7", done?.senderIdentity)
        assertEquals(1700000000000L, done?.timestamp)
    }

    @Test
    fun `accepts parts that arrive out of order`() {
        val assembler = ChatChunkAssembler()
        assembler.accept(meta("m1", messageChunks = 3), topic)

        assertNull(assembler.accept(chunk("m1", 2, "three"), topic))
        assertNull(assembler.accept(chunk("m1", 0, "one "), topic))

        assertEquals("one two three", assembler.accept(chunk("m1", 1, "two "), topic)?.text)
    }

    @Test
    fun `interleaves two senders without mixing their parts`() {
        val assembler = ChatChunkAssembler()
        assembler.accept(meta("m1", messageChunks = 2), topic)
        assembler.accept(meta("m2", messageChunks = 2), topic)

        assembler.accept(chunk("m1", 0, "first-"), topic)
        assembler.accept(chunk("m2", 0, "second-"), topic)

        assertEquals("second-b", assembler.accept(chunk("m2", 1, "b"), topic)?.text)
        assertEquals("first-a", assembler.accept(chunk("m1", 1, "a"), topic)?.text)
    }

    @Test
    fun `delivers a message once, even if its last part repeats`() {
        val assembler = ChatChunkAssembler()
        assembler.accept(meta("m1", messageChunks = 1), topic)

        assertEquals("only", assembler.accept(chunk("m1", 0, "only"), topic)?.text)
        assertNull(assembler.accept(chunk("m1", 0, "only"), topic))
    }

    @Test
    fun `ignores a part whose header never arrived`() {
        assertNull(ChatChunkAssembler().accept(chunk("unknown", 0, "orphan"), topic))
    }

    @Test
    fun `ignores a part indexed past what the header announced`() {
        val assembler = ChatChunkAssembler()
        assembler.accept(meta("m1", messageChunks = 1), topic)

        assertNull(assembler.accept(chunk("m1", 5, "out of range"), topic))
    }

    @Test
    fun `keeps the parts already in hand when a header repeats`() {
        val assembler = ChatChunkAssembler()
        assembler.accept(meta("m1", messageChunks = 2), topic)
        assembler.accept(chunk("m1", 0, "one "), topic)
        assembler.accept(meta("m1", messageChunks = 2), topic)

        assertEquals("one two", assembler.accept(chunk("m1", 1, "two"), topic)?.text)
    }

    @Test
    fun `drops a message whose parts stopped arriving`() {
        val assembler = ChatChunkAssembler(maxAgeMs = 1_000L)
        assembler.accept(meta("m1", messageChunks = 2), topic, now = 0L)
        assembler.accept(chunk("m1", 0, "one "), topic, now = 0L)

        assertNull(assembler.accept(chunk("m1", 1, "two"), topic, now = 5_000L))
    }

    @Test
    fun `forgets everything in flight when the room is left`() {
        val assembler = ChatChunkAssembler()
        assembler.accept(meta("m1", messageChunks = 2), topic)
        assembler.accept(chunk("m1", 0, "one "), topic)
        assembler.clear()

        assertNull(assembler.accept(chunk("m1", 1, "two"), topic))
    }

    @Test
    fun `refuses a header announcing more parts than any real message has`() {
        val assembler = ChatChunkAssembler()
        assembler.accept(meta("m1", messageChunks = 100_000), topic)

        assertNull(assembler.accept(chunk("m1", 0, "nope"), topic))
    }

    @Test
    fun `rebuilds a long message this app sent itself`() {
        val text = "x".repeat(150_000)

        assertEquals(text, assembleOwnOutput(text)?.text)
    }

    @Test
    fun `rebuilds a long message of emoji without corrupting them`() {
        val text = "🎉".repeat(40_000)

        assertEquals(text, assembleOwnOutput(text)?.text)
    }
}
