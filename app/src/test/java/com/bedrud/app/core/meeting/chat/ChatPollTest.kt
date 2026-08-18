package com.bedrud.app.core.meeting.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatPollTest {

    private fun poll() = ChatPoll(
        id = "p-1",
        question = "Lunch?",
        options = listOf(
            ChatPollOption(id = "o-1", text = "Now"),
            ChatPollOption(id = "o-2", text = "Later"),
        ),
    )

    @Test
    fun `a vote is recorded against the voter`() {
        assertEquals(mapOf("u-1" to "o-2"), poll().withVote("u-1", "o-2").votes)
    }

    @Test
    fun `voting again moves this person's vote`() {
        val voted = poll().withVote("u-1", "o-2").withVote("u-1", "o-1")

        assertEquals(mapOf("u-1" to "o-1"), voted.votes)
    }

    @Test
    fun `a vote for an option this poll does not offer changes nothing`() {
        val original = poll().withVote("u-1", "o-1")

        assertEquals(original, original.withVote("u-2", "o-404"))
    }

    @Test
    fun `counts each option, and rounds the share to whole percent`() {
        val voted = poll()
            .withVote("u-1", "o-1")
            .withVote("u-2", "o-1")
            .withVote("u-3", "o-2")

        val results = voted.results()

        assertEquals(listOf(2, 1), results.map { it.count })
        assertEquals(listOf(67, 33), results.map { it.percent })
        assertEquals(listOf("u-1", "u-2"), results.first().voters)
        assertEquals(3, voted.totalVotes)
    }

    @Test
    fun `an unvoted poll shows no share at all, rather than dividing by nobody`() {
        assertEquals(listOf(0, 0), poll().results().map { it.percent })
    }

    @Test
    fun `builds a poll from what was typed, dropping the answers left blank`() {
        val built = newPoll("  Lunch?  ", listOf("Now", "   ", "Later"))

        assertEquals("Lunch?", built?.question)
        assertEquals(listOf("Now", "Later"), built?.options?.map { it.text })
    }

    @Test
    fun `refuses a poll that is not a question, or has nothing to choose between`() {
        assertNull(newPoll("", listOf("Now", "Later")))
        assertNull(newPoll("Lunch?", listOf("Now")))
        assertNull(newPoll("Lunch?", listOf("Now", "  ")))
    }

    @Test
    fun `gives every poll and every answer an id of its own`() {
        val first = newPoll("Lunch?", listOf("Now", "Later"))!!
        val second = newPoll("Lunch?", listOf("Now", "Later"))!!

        assertNotEquals(first.id, second.id)
        assertNotEquals(first.options.first().id, first.options.last().id)
    }
}
