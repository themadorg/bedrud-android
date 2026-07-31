package com.bedrud.app.core.deeplink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BedrudURLParserTest {
    @Test
    fun `parseJoinInput handles plain room slug`() {
        assertEquals("qjl-jmsw-eha", BedrudURLParser.parseJoinInput("qjl-jmsw-eha"))
    }


    @Test
    fun `parseJoinInput returns null for invalid input`() {
        assertNull(BedrudURLParser.parseJoinInput(""))
        assertNull(BedrudURLParser.parseJoinInput("   "))
    }

    @Test
    fun `matchesServer ignores the instance's trailing slash and case`() {
        assertTrue(BedrudURLParser.matchesServer("https://Bedrud.xyz/", "https://bedrud.xyz"))
        assertTrue(BedrudURLParser.matchesServer("https://bedrud.xyz", "https://bedrud.xyz"))
    }

    @Test
    fun `matchesServer rejects a different host`() {
        assertFalse(BedrudURLParser.matchesServer("https://bedrud.xyz/", "https://meet.bshkena.ir"))
    }

    @Test
    fun `parse and matchesServer resolve a pasted link to its own server, not a lookalike host`() {
        val parsed = BedrudURLParser.parse("https://meet.bshkena.ir/m/qjl-jmsw-eha")!!
        assertEquals("qjl-jmsw-eha", parsed.roomName)
        assertTrue(BedrudURLParser.matchesServer("https://meet.bshkena.ir/", parsed.serverBaseURL))
        assertFalse(BedrudURLParser.matchesServer("https://bedrud.xyz/", parsed.serverBaseURL))
    }
}