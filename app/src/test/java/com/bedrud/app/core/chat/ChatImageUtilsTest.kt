package com.bedrud.app.core.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val ServerURL = "https://bedrud.example"

/**
 * Where a chat image's address leads, and whether the account's token may go with it. The address
 * comes from whoever sent the message, so only the account's own server, reached the same way the
 * app reaches it, is trusted with the token.
 */
class ChatImageUtilsTest {

    @Test
    fun `should resolve a path against the server`() {
        assertEquals("$ServerURL/uploads/a.png", ChatImageUtils.resolveChatImageUrl(ServerURL, "/uploads/a.png"))
        assertEquals("$ServerURL/uploads/a.png", ChatImageUtils.resolveChatImageUrl(ServerURL, "uploads/a.png"))
        assertEquals("$ServerURL/uploads/a.png", ChatImageUtils.resolveChatImageUrl("$ServerURL/", "/uploads/a.png"))
    }

    @Test
    fun `should leave an absolute or inlined address as it is`() {
        assertEquals("https://elsewhere.example/a.png", ChatImageUtils.resolveChatImageUrl(ServerURL, "https://elsewhere.example/a.png"))
        assertEquals("http://elsewhere.example/a.png", ChatImageUtils.resolveChatImageUrl(ServerURL, "http://elsewhere.example/a.png"))
        assertEquals("data:image/png;base64,AAAA", ChatImageUtils.resolveChatImageUrl(ServerURL, "data:image/png;base64,AAAA"))
    }

    @Test
    fun `should trust a path on the server with the token`() {
        assertTrue(ChatImageUtils.isOnServer(ServerURL, "/uploads/chat/a.png"))
    }

    @Test
    fun `should trust the server's own absolute address with the token`() {
        assertTrue(ChatImageUtils.isOnServer(ServerURL, "$ServerURL/uploads/chat/a.png"))
        assertTrue(ChatImageUtils.isOnServer(ServerURL, "https://BEDRUD.example:443/uploads/chat/a.png"))
    }

    @Test
    fun `should not trust another host with the token`() {
        assertFalse(ChatImageUtils.isOnServer(ServerURL, "https://elsewhere.example/a.png"))
    }

    /** A host that merely starts or ends like the server's is another host. */
    @Test
    fun `should not trust a look-alike host with the token`() {
        assertFalse(ChatImageUtils.isOnServer(ServerURL, "https://bedrud.example.elsewhere.example/a.png"))
        assertFalse(ChatImageUtils.isOnServer(ServerURL, "https://elsewhere.example/bedrud.example/a.png"))
        assertFalse(ChatImageUtils.isOnServer(ServerURL, "https://bedrud.example@elsewhere.example/a.png"))
    }

    @Test
    fun `should not trust the server on another port with the token`() {
        assertFalse(ChatImageUtils.isOnServer(ServerURL, "https://bedrud.example:8443/a.png"))
    }

    /** Plain http would carry the token in the clear, even to the right host. */
    @Test
    fun `should not trust the server over plain http with the token`() {
        assertFalse(ChatImageUtils.isOnServer(ServerURL, "http://bedrud.example/a.png"))
    }

    @Test
    fun `should not treat an inlined image as on the server`() {
        assertFalse(ChatImageUtils.isOnServer(ServerURL, "data:image/png;base64,AAAA"))
    }
}
