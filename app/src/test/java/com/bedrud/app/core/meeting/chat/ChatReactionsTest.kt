package com.bedrud.app.core.meeting.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatReactionsTest {

    @Test
    fun `a first tap adds a reaction`() {
        assertEquals(mapOf("u-1" to "👍"), emptyMap<String, String>().toggled("u-1", "👍"))
    }

    @Test
    fun `the same reaction again takes it back`() {
        val reactions = mapOf("u-1" to "👍")

        assertEquals(emptyMap<String, String>(), reactions.toggled("u-1", "👍"))
    }

    @Test
    fun `a different reaction moves this person's one, rather than adding a second`() {
        val reactions = mapOf("u-1" to "👍")

        assertEquals(mapOf("u-1" to "🎉"), reactions.toggled("u-1", "🎉"))
    }

    @Test
    fun `one person's reaction leaves everybody else's alone`() {
        val reactions = mapOf("u-1" to "👍", "u-2" to "👍")

        assertEquals(mapOf("u-1" to "👍", "u-2" to "🔥"), reactions.toggled("u-2", "🔥"))
    }

    @Test
    fun `a reaction from nobody is ignored`() {
        assertEquals(emptyMap<String, String>(), emptyMap<String, String>().toggled("", "👍"))
    }

    @Test
    fun `groups the chips most-chosen first, and marks the reader's own`() {
        val reactions = mapOf("u-1" to "👍", "u-2" to "🎉", "u-3" to "👍", "u-me" to "🔥")

        val chips = reactions.grouped(currentIdentity = "u-me")

        assertEquals(listOf("👍", "🎉", "🔥"), chips.map { it.emoji })
        assertEquals(listOf(2, 1, 1), chips.map { it.count })
        assertEquals(listOf(false, false, true), chips.map { it.mine })
    }

    @Test
    fun `chips on equal counts keep the order they were first reacted with`() {
        val reactions = mapOf("u-1" to "🎉", "u-2" to "👍", "u-3" to "🎉", "u-4" to "👍")

        assertEquals(listOf("🎉", "👍"), reactions.grouped("u-me").map { it.emoji })
    }

    @Test
    fun `keeps whatever the quick picker offers`() {
        assertTrue(QuickReactions.all { isReactionEmoji(it) })
    }

    @Test
    fun `refuses text wearing a chip`() {
        assertFalse(isReactionEmoji("not an emoji"))
        assertFalse(isReactionEmoji(""))
        assertFalse(isReactionEmoji("🎉".repeat(40)))
    }

    @Test
    fun `the breakdown lists everyone who chose each emoji, most-chosen first`() {
        val reactions = mapOf("u-1" to "👍", "u-2" to "🎉", "u-3" to "👍", "u-me" to "🔥")

        val sections = reactions.breakdown()

        assertEquals(listOf("👍", "🎉", "🔥"), sections.map { it.emoji })
        assertEquals(listOf("u-1", "u-3"), sections.first().identities)
    }

    @Test
    fun `the breakdown keeps stray text out, the way the chips do`() {
        val reactions = mapOf("u-1" to "👍", "u-2" to "not an emoji")

        assertEquals(listOf("👍"), reactions.breakdown().map { it.emoji })
    }

    @Test
    fun `no reactions is no breakdown, rather than an empty section`() {
        assertEquals(emptyList<ReactionBreakdown>(), emptyMap<String, String>().breakdown())
    }
}
