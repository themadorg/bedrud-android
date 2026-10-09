package com.bedrud.app.core.livekit

import android.app.Application
import android.content.Context
import com.bedrud.app.testutil.InMemorySharedPreferences
import com.bedrud.app.ui.screens.settings.SettingsStore
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test

class RoomManagerTest {

    private fun roomManager(settingsStore: SettingsStore = settingsStore()): RoomManager =
        RoomManager(mockk<Application>(relaxed = true), settingsStore)

    private fun settingsStore(): SettingsStore {
        val preferences = InMemorySharedPreferences()
        val context = mockk<Context> {
            every { getSharedPreferences(any(), any()) } returns preferences
        }
        return SettingsStore(context)
    }

    /**
     * Deafening used to wait for the room to hear about it first, and that announcement can stall
     * for seconds while the outgoing connection is not up. The toggle now lands before anything
     * goes over the network.
     */
    @Test
    fun `should deafen at once when toggled`() {
        val settings = settingsStore()
        val manager = roomManager(settings)

        manager.toggleDeafen()

        assertTrue(manager.isDeafened.value)
        assertTrue(settings.getDeafened())
    }

    @Test
    fun `should end where it started after two quick toggles`() {
        val manager = roomManager()

        manager.toggleDeafen()
        manager.toggleDeafen()

        assertFalse(manager.isDeafened.value)
    }

    @Test
    fun `ChatMessage data class init and defaults`() {
        val msg = ChatMessage(id = "m1", senderName = "Alice", senderIdentity = "u-1", text = "Hello")
        assertEquals("m1", msg.id)
        assertEquals("Alice", msg.senderName)
        assertEquals("u-1", msg.senderIdentity)
        assertEquals("Hello", msg.text)
        assertTrue(msg.timestamp > 0)
        assertFalse(msg.isLocal)
    }

    @Test
    fun `ChatMessage with custom values`() {
        val msg = ChatMessage(
            id = "m2", senderName = "Bob", senderIdentity = "u-2", text = "Hi",
            timestamp = 999L, isLocal = true
        )
        assertEquals("Bob", msg.senderName)
        assertEquals("Hi", msg.text)
        assertEquals(999L, msg.timestamp)
        assertTrue(msg.isLocal)
    }

    @Test
    fun `ConnectionState enum has all expected values`() {
        val values = ConnectionState.values()
        assertEquals(5, values.size)
        assertTrue(values.contains(ConnectionState.DISCONNECTED))
        assertTrue(values.contains(ConnectionState.CONNECTING))
        assertTrue(values.contains(ConnectionState.CONNECTED))
        assertTrue(values.contains(ConnectionState.RECONNECTING))
        assertTrue(values.contains(ConnectionState.FAILED))
    }

    @Test
    fun `ConnectionState valueOf round-trip`() {
        assertEquals(ConnectionState.DISCONNECTED, ConnectionState.valueOf("DISCONNECTED"))
        assertEquals(ConnectionState.CONNECTED, ConnectionState.valueOf("CONNECTED"))
        assertEquals(ConnectionState.FAILED, ConnectionState.valueOf("FAILED"))
    }

    @Test
    fun `ChatMessage equality`() {
        val msg1 = ChatMessage("m1", "A", "u-1", "Hi", timestamp = 100L, isLocal = false)
        val msg2 = ChatMessage("m1", "A", "u-1", "Hi", timestamp = 100L, isLocal = false)
        assertEquals(msg1, msg2)
        assertEquals(msg1.hashCode(), msg2.hashCode())

        val msg3 = ChatMessage("m1", "B", "u-1", "Hi", timestamp = 100L, isLocal = false)
        assertNotEquals(msg1, msg3)
    }
}
