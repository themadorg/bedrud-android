package com.bedrud.app.core.deeplink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}