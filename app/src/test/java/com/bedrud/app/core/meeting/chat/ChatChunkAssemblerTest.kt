package com.bedrud.app.core.meeting.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatChunkAssemblerTest {

    private val topic = ChatWire.CHAT_DATA_TOPIC

    private fun packet(json: String): ByteArray = json.toByteArray(Charsets.UTF_8)

    /** The message a packet completed, or null when it completed nothing — or was not a message. */
    private fun ChatWire.Inbound?.chat(): ChatWire.IncomingChat? =
        (this as? ChatWire.Inbound.Complete)?.chat

    private fun meta(
        id: String,
        messageChunks: Int,
        attachmentChunks: Int = 0,
        pollChunks: Int = 0,
    ) = packet(
        """
        {"type":"chat_chunk_meta","id":"$id","timestamp":1700000000000,
         "senderName":"Sara","senderIdentity":"u-7",
         "messageChunks":$messageChunks,"attachmentChunks":$attachmentChunks,"pollChunks":$pollChunks}
        """.trimIndent()
    )

    private fun chunk(id: String, index: Int, part: String, section: String = "message") =
        packet("""{"type":"chat_chunk","id":"$id","kind":"$section","index":$index,"part":"$part"}""")

    /** Feeds this app's own packets back in, which is what the other end has to be able to do. */
    private fun assembleOwnOutput(text: String, poll: ChatPoll? = null): ChatWire.IncomingChat? {
        val assembler = ChatChunkAssembler()
        return ChatWire.encodeChat("Sara", "u-7", text, emptyList(), poll = poll)
            .mapNotNull { assembler.accept(it, topic).chat() }
            .lastOrNull()
    }

    @Test
    fun `passes a single-packet message straight through`() {
        val assembler = ChatChunkAssembler()
        val raw = packet("""{"type":"chat","id":"m1","senderName":"Sara","message":"hi"}""")

        assertEquals("hi", assembler.accept(raw, topic).chat()?.text)
    }

    @Test
    fun `hands a reaction straight back, since there is nothing to assemble`() {
        val assembler = ChatChunkAssembler()
        val raw = packet("""{"type":"reaction","messageId":"m1","emoji":"🎉","voterIdentity":"u-7"}""")

        val reaction = assembler.accept(raw, topic) as? ChatWire.Inbound.Reaction

        assertEquals("m1", reaction?.messageId)
        assertEquals("🎉", reaction?.emoji)
    }

    @Test
    fun `holds a split message until its last part lands`() {
        val assembler = ChatChunkAssembler()

        assertNull(assembler.accept(meta("m1", messageChunks = 3), topic))
        assertNull(assembler.accept(chunk("m1", 0, "one "), topic))
        assertNull(assembler.accept(chunk("m1", 1, "two "), topic))
        val done = assembler.accept(chunk("m1", 2, "three"), topic).chat()

        assertEquals("one two three", done?.text)
        assertEquals("Sara", done?.senderName)
        assertEquals("u-7", done?.senderIdentity)
        assertEquals(1700000000000L, done?.timestamp)
    }

    @Test
    fun `waits for the poll parts as well as the message ones`() {
        val assembler = ChatChunkAssembler()
        val poll = """{\"id\":\"p-1\",\"question\":\"Lunch?\",\"options\":[{\"id\":\"o-1\",\"text\":\"Now\"},{\"id\":\"o-2\",\"text\":\"Later\"}]}"""

        assembler.accept(meta("m1", messageChunks = 1, pollChunks = 1), topic)
        assertNull(assembler.accept(chunk("m1", 0, "vote please"), topic))
        val done = assembler.accept(chunk("m1", 0, poll, section = "poll"), topic).chat()

        assertEquals("vote please", done?.text)
        assertEquals("Lunch?", done?.poll?.question)
        assertEquals(listOf("Now", "Later"), done?.poll?.options?.map { it.text })
    }

    @Test
    fun `accepts parts that arrive out of order`() {
        val assembler = ChatChunkAssembler()
        assembler.accept(meta("m1", messageChunks = 3), topic)

        assertNull(assembler.accept(chunk("m1", 2, "three"), topic))
        assertNull(assembler.accept(chunk("m1", 0, "one "), topic))

        assertEquals("one two three", assembler.accept(chunk("m1", 1, "two "), topic).chat()?.text)
    }

    @Test
    fun `interleaves two senders without mixing their parts`() {
        val assembler = ChatChunkAssembler()
        assembler.accept(meta("m1", messageChunks = 2), topic)
        assembler.accept(meta("m2", messageChunks = 2), topic)

        assembler.accept(chunk("m1", 0, "first-"), topic)
        assembler.accept(chunk("m2", 0, "second-"), topic)

        assertEquals("second-b", assembler.accept(chunk("m2", 1, "b"), topic).chat()?.text)
        assertEquals("first-a", assembler.accept(chunk("m1", 1, "a"), topic).chat()?.text)
    }

    @Test
    fun `delivers a message once, even if its last part repeats`() {
        val assembler = ChatChunkAssembler()
        assembler.accept(meta("m1", messageChunks = 1), topic)

        assertEquals("only", assembler.accept(chunk("m1", 0, "only"), topic).chat()?.text)
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

        assertEquals("one two", assembler.accept(chunk("m1", 1, "two"), topic).chat()?.text)
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

    @Test
    fun `rebuilds a poll that had to travel alongside a long message`() {
        val poll = newPoll("Lunch?", listOf("Now", "Later"))!!

        assertEquals(poll, assembleOwnOutput("x".repeat(150_000), poll = poll)?.poll)
    }
}
