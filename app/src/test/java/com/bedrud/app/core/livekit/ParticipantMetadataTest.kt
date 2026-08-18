package com.bedrud.app.core.livekit

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParticipantMetadataTest {

    @Test
    fun `reads the avatar`() {
        assertEquals(
            "https://example.test/a.png",
            ParticipantMetadata.avatarUrl("""{"avatarUrl":"https://example.test/a.png"}"""),
        )
    }

    @Test
    fun `treats a missing, blank or unreadable avatar as none`() {
        assertNull(ParticipantMetadata.avatarUrl(null))
        assertNull(ParticipantMetadata.avatarUrl(""))
        assertNull(ParticipantMetadata.avatarUrl("{}"))
        assertNull(ParticipantMetadata.avatarUrl("""{"avatarUrl":""}"""))
        assertNull(ParticipantMetadata.avatarUrl("not json"))
    }

    @Test
    fun `reads the chat block`() {
        assertTrue(ParticipantMetadata.isChatBlocked("""{"chatBlocked":true}"""))
        assertFalse(ParticipantMetadata.isChatBlocked("""{"chatBlocked":false}"""))
    }

    @Test
    fun `treats a missing or unreadable block as not blocked`() {
        assertFalse(ParticipantMetadata.isChatBlocked(null))
        assertFalse(ParticipantMetadata.isChatBlocked("{}"))
        assertFalse(ParticipantMetadata.isChatBlocked("not json"))
    }

    @Test
    fun `keeps the server's flags when writing the avatar`() {
        // The whole point of merging: setting an avatar must not unblock the person.
        val merged = ParticipantMetadata.withAvatarUrl(
            """{"chatBlocked":true}""",
            "https://example.test/a.png",
        )

        assertTrue(ParticipantMetadata.isChatBlocked(merged))
        assertEquals("https://example.test/a.png", ParticipantMetadata.avatarUrl(merged))
    }

    @Test
    fun `writes an avatar onto absent or unreadable metadata`() {
        listOf(null, "", "not json").forEach { start ->
            val merged = ParticipantMetadata.withAvatarUrl(start, "https://example.test/a.png")
            assertEquals("https://example.test/a.png", JSONObject(merged).getString("avatarUrl"))
        }
    }

    @Test
    fun `replaces an avatar that was already set`() {
        val merged = ParticipantMetadata.withAvatarUrl(
            """{"avatarUrl":"https://example.test/old.png"}""",
            "https://example.test/new.png",
        )

        assertEquals("https://example.test/new.png", ParticipantMetadata.avatarUrl(merged))
    }
}
