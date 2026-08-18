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
    @Test
    fun `reads the deafened flag`() {
        assertTrue(ParticipantMetadata.isDeafened("""{"deafened":true}"""))
        assertFalse(ParticipantMetadata.isDeafened("""{"deafened":false}"""))
    }

    @Test
    fun `treats a missing or unreadable deafened flag as hearing`() {
        assertFalse(ParticipantMetadata.isDeafened(null))
        assertFalse(ParticipantMetadata.isDeafened(""))
        assertFalse(ParticipantMetadata.isDeafened("{}"))
        assertFalse(ParticipantMetadata.isDeafened("not json"))
    }

    @Test
    fun `setting deafened leaves every other field alone`() {
        // The server keeps moderation flags on this same blob; assigning over it would clear them.
        val merged = ParticipantMetadata.withDeafened(
            """{"avatarUrl":"https://example.test/a.png","chatBlocked":true}""",
            true,
        )
        val json = JSONObject(merged)

        assertTrue(json.getBoolean("deafened"))
        assertEquals("https://example.test/a.png", json.getString("avatarUrl"))
        assertTrue(json.getBoolean("chatBlocked"))
    }

    @Test
    fun `undeafening writes the flag rather than dropping it`() {
        // A missing field reads as hearing, but an explicit false is what tells a client that has
        // already seen the true to change its mind.
        val json = JSONObject(ParticipantMetadata.withDeafened("""{"deafened":true}""", false))

        assertTrue(json.has("deafened"))
        assertFalse(json.getBoolean("deafened"))
    }
}
