package com.bedrud.app.core.meeting.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatLinksTest {

    private fun urls(text: String) = findChatLinks(text).map { it.url }

    @Test
    fun `finds a link carrying its own scheme`() {
        assertEquals(
            listOf("https://example.com/docs/quarterly-review"),
            urls("Agenda is at https://example.com/docs/quarterly-review"),
        )
    }

    @Test
    fun `finds every link in one message`() {
        assertEquals(
            listOf("https://example.com", "https://bedrud.xyz/m/standup"),
            urls("read https://example.com then join https://bedrud.xyz/m/standup"),
        )
    }

    @Test
    fun `finds a www link with no scheme`() {
        assertEquals(listOf("www.example.com/path"), urls("see www.example.com/path"))
    }

    @Test
    fun `finds a bare host once it carries a path`() {
        assertEquals(listOf("bedrud.xyz/m/standup"), urls("join bedrud.xyz/m/standup"))
    }

    /** The whole reason the bare-host branch demands a path: prose is full of dotted words. */
    @Test
    fun `leaves prose alone`() {
        assertEquals(emptyList<String>(), urls("see you Sept. 11, i.e. next week"))
        assertEquals(emptyList<String>(), urls("scored 3.5 out of 10, etc."))
        assertEquals(emptyList<String>(), urls("bedrud.xyz"))
    }

    @Test
    fun `drops the punctuation that ended the sentence`() {
        assertEquals(listOf("https://example.com/a"), urls("open https://example.com/a."))
        assertEquals(listOf("https://example.com/a"), urls("open (https://example.com/a),"))
    }

    @Test
    fun `reports where the link sits, so it can be marked up in place`() {
        val text = "go to https://example.com/a now"
        val link = findChatLinks(text).single()

        assertEquals("https://example.com/a", text.substring(link.range))
    }

    @Test
    fun `finds a link in a right-to-left message`() {
        val text = "لینک جلسه https://bedrud.xyz/m/standup است"
        val link = findChatLinks(text).single()

        assertEquals("https://bedrud.xyz/m/standup", link.url)
        assertEquals("https://bedrud.xyz/m/standup", text.substring(link.range))
    }

    @Test
    fun `a message with no link has none`() {
        assertTrue(findChatLinks("just saying hello").isEmpty())
        assertTrue(findChatLinks("").isEmpty())
    }
}
