package com.bedrud.app.core.rooms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PendingRoomTest {

    private val roomServer = "server-a"
    private val otherServer = "server-b"
    private val roomName = "obd-hzpi-dfv"
    private val otherRoomName = "oqc-qpka-bvw"

    @Test
    fun `should hand over a held room once signed in on its server`() {
        val pendingRoom = PendingRoom()
        pendingRoom.hold(roomName, roomServer)

        assertEquals(roomName, pendingRoom.take(activeServerId = roomServer, isSignedIn = true))
    }

    @Test
    fun `should keep a held room while its server has nobody signed in`() {
        val pendingRoom = PendingRoom()
        pendingRoom.hold(roomName, roomServer)

        assertNull(pendingRoom.take(activeServerId = roomServer, isSignedIn = false))
        assertEquals(RoomRequest(roomName, roomServer), pendingRoom.request.value)
    }

    @Test
    fun `should hand over a room held across a sign-in once the sign-in lands`() {
        val pendingRoom = PendingRoom()
        pendingRoom.hold(roomName, roomServer)
        pendingRoom.take(activeServerId = roomServer, isSignedIn = false)

        assertEquals(roomName, pendingRoom.take(activeServerId = roomServer, isSignedIn = true))
    }

    @Test
    fun `should hand over a held room only once`() {
        val pendingRoom = PendingRoom()
        pendingRoom.hold(roomName, roomServer)
        pendingRoom.take(activeServerId = roomServer, isSignedIn = true)

        assertNull(pendingRoom.take(activeServerId = roomServer, isSignedIn = true))
        assertNull(pendingRoom.request.value)
    }

    @Test
    fun `should forget a held room once another server is active`() {
        val pendingRoom = PendingRoom()
        pendingRoom.hold(roomName, roomServer)

        assertNull(pendingRoom.take(activeServerId = otherServer, isSignedIn = true))
        assertNull(pendingRoom.request.value)
    }

    @Test
    fun `should not open a forgotten room on returning to its server`() {
        val pendingRoom = PendingRoom()
        pendingRoom.hold(roomName, roomServer)
        pendingRoom.take(activeServerId = otherServer, isSignedIn = false)

        assertNull(pendingRoom.take(activeServerId = roomServer, isSignedIn = true))
    }

    @Test
    fun `should replace a held room with a newer one`() {
        val pendingRoom = PendingRoom()
        pendingRoom.hold(roomName, roomServer)
        pendingRoom.hold(otherRoomName, otherServer)

        assertEquals(otherRoomName, pendingRoom.take(activeServerId = otherServer, isSignedIn = true))
    }
}
