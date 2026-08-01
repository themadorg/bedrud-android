package com.bedrud.app.core.instance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerUrlCanonicalizerTest {
    @Test
    fun `bare host defaults to https`() {
        assertEquals("https://meet.example.com/", ServerUrlCanonicalizer.canonicalize("meet.example.com"))
    }

    @Test
    fun `explicit http scheme is preserved for local dev servers`() {
        assertEquals("http://localhost:3000/", ServerUrlCanonicalizer.canonicalize("http://localhost:3000"))
    }

    @Test
    fun `explicit https scheme is preserved`() {
        assertEquals("https://meet.example.com/", ServerUrlCanonicalizer.canonicalize("https://meet.example.com"))
    }

    @Test
    fun `a pasted full URL with a path resolves to just the server address`() {
        assertEquals(
            "https://meet.example.com/",
            ServerUrlCanonicalizer.canonicalize("https://meet.example.com/login"),
        )
        assertEquals(
            "https://meet.example.com/",
            ServerUrlCanonicalizer.canonicalize("https://meet.example.com/some/deep/path"),
        )
    }

    @Test
    fun `a pasted URL with a query string or fragment resolves to just the server address`() {
        assertEquals(
            "https://meet.example.com/",
            ServerUrlCanonicalizer.canonicalize("https://meet.example.com?redirect=/login"),
        )
        assertEquals(
            "https://meet.example.com/",
            ServerUrlCanonicalizer.canonicalize("https://meet.example.com#section"),
        )
    }

    @Test
    fun `port is preserved`() {
        assertEquals("https://meet.example.com:8443/", ServerUrlCanonicalizer.canonicalize("meet.example.com:8443"))
    }

    @Test
    fun `whitespace is stripped`() {
        assertEquals("https://meet.example.com/", ServerUrlCanonicalizer.canonicalize("  meet.example.com  "))
    }

    @Test
    fun `blank or malformed input returns null`() {
        assertNull(ServerUrlCanonicalizer.canonicalize(""))
        assertNull(ServerUrlCanonicalizer.canonicalize("   "))
        assertNull(ServerUrlCanonicalizer.canonicalize("not a valid host!"))
    }
}
