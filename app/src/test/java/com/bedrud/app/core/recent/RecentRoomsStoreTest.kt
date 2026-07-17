package com.bedrud.app.core.recent

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentRoomsStoreTest {

    @Test
    fun `recentRoomsNotInApiList excludes current-server rooms already in API list`() {
        val recent = listOf(
            RecentRoom("room-a", "inst-1", "Server A"),
            RecentRoom("room-b", "inst-1", "Server A"),
            RecentRoom("room-c", "inst-2", "Server B"),
        )

        val result = recentRoomsNotInApiList(recent, setOf("room-a"), "inst-1")

        assertEquals(
            listOf("room-b", "room-c"),
            result.map { it.roomName },
        )
    }

    @Test
    fun `recentRoomsNotInApiList keeps other-server rooms`() {
        val recent = listOf(
            RecentRoom("shared-room", "inst-2", "Server B"),
        )

        val result = recentRoomsNotInApiList(recent, setOf("shared-room"), "inst-1")

        assertEquals(listOf("shared-room"), result.map { it.roomName })
    }
}