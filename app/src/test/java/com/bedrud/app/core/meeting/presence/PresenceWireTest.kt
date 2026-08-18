package com.bedrud.app.core.meeting.presence

import com.bedrud.app.core.meeting.chat.ChatWire
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PresenceWireTest {

    private fun bytes(json: String) = json.toByteArray(Charsets.UTF_8)

    @Test
    fun `a deafen announcement survives the round trip`() {
        val encoded = PresenceWire.encodeDeafenState("user-1", true)

        val decoded = PresenceWire.parseDeafenState(encoded, PresenceWire.PRESENCE_DATA_TOPIC)

        assertEquals(PresenceWire.DeafenState("user-1", true), decoded)
    }

    @Test
    fun `undeafening is announced as its own message, not as silence`() {
        val encoded = PresenceWire.encodeDeafenState("user-1", false)

        val decoded = PresenceWire.parseDeafenState(encoded, PresenceWire.PRESENCE_DATA_TOPIC)

        assertEquals(PresenceWire.DeafenState("user-1", false), decoded)
    }

    @Test
    fun `reads what the web client sends`() {
        // Transcribed from the web client's advertiseDeafenedState: the two clients share this
        // wire, so a change to either side has to keep this passing.
        val fromWeb = bytes("""{"type":"deafen_state","identity":"web-guest","deafened":true}""")

        assertEquals(
            PresenceWire.DeafenState("web-guest", true),
            PresenceWire.parseDeafenState(fromWeb, PresenceWire.PRESENCE_DATA_TOPIC),
        )
    }

    @Test
    fun `an announcement naming nobody is dropped`() {
        // Applying it would have to guess whose state changed, and the guess badges the wrong face.
        val anonymous = bytes("""{"type":"deafen_state","deafened":true}""")
        val blank = bytes("""{"type":"deafen_state","identity":"","deafened":true}""")

        assertNull(PresenceWire.parseDeafenState(anonymous, PresenceWire.PRESENCE_DATA_TOPIC))
        assertNull(PresenceWire.parseDeafenState(blank, PresenceWire.PRESENCE_DATA_TOPIC))
    }

    @Test
    fun `another topic is not presence, whatever it contains`() {
        val encoded = PresenceWire.encodeDeafenState("user-1", true)

        assertNull(PresenceWire.parseDeafenState(encoded, ChatWire.CHAT_DATA_TOPIC))
        assertNull(PresenceWire.parseDeafenState(encoded, null))
    }

    @Test
    fun `other presence messages and malformed payloads are ignored`() {
        // Presence carries more than deafen — a profile change shares the topic and must not be
        // read as one.
        val profile = bytes("""{"type":"profile_changed","identity":"user-1","name":"Ada"}""")

        assertNull(PresenceWire.parseDeafenState(profile, PresenceWire.PRESENCE_DATA_TOPIC))
        assertNull(PresenceWire.parseDeafenState(bytes("not json"), PresenceWire.PRESENCE_DATA_TOPIC))
        assertNull(PresenceWire.parseDeafenState(ByteArray(0), PresenceWire.PRESENCE_DATA_TOPIC))
    }

    @Test
    fun `chat is not mistaken for presence`() {
        // Both wires ride the same data channel, so each has to refuse the other's payloads.
        val chat = ChatWire.encodeChat(
            senderName = "Ada",
            senderIdentity = "user-1",
            text = "hello",
            attachments = emptyList(),
        ).first()

        assertNull(PresenceWire.parseDeafenState(chat, ChatWire.CHAT_DATA_TOPIC))
        assertNull(PresenceWire.parseDeafenState(chat, PresenceWire.PRESENCE_DATA_TOPIC))
    }
}
