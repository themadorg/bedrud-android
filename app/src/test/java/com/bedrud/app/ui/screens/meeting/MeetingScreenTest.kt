package com.bedrud.app.ui.screens.meeting

import com.bedrud.app.core.livekit.ConnectionState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeetingScreenTest {

    @Test
    fun `should count a connection to this screen's room as its own`() {
        assertTrue(isOwnConnection(ConnectionState.CONNECTED, connectedRoomName = ThisRoom, roomName = ThisRoom))
    }

    /** The room being left is still connected for a moment after the next room's screen appears. */
    @Test
    fun `should not count a connection left over from the room being left as its own`() {
        assertFalse(isOwnConnection(ConnectionState.CONNECTED, connectedRoomName = LeftRoom, roomName = ThisRoom))
    }

    @Test
    fun `should not count a connection still being made as its own`() {
        assertFalse(isOwnConnection(ConnectionState.CONNECTING, connectedRoomName = ThisRoom, roomName = ThisRoom))
    }

    private companion object {
        const val ThisRoom = "oqc-qpka-bvw"
        const val LeftRoom = "obd-hzpi-dfv"
    }
}
