package com.bedrud.app.core.recent

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentRoomsStoreTest {

    @Test
    fun `recentRoomsNotInApiList keeps active-server rooms not already in the API list`() {
        val recent = listOf(
            RecentRoom("room-a", "inst-1", "Server A"),
            RecentRoom("room-b", "inst-1", "Server A"),
            RecentRoom("room-c", "inst-2", "Server B"),
        )

        val result = recentRoomsNotInApiList(recent, setOf("room-a"), "inst-1")

        // room-a is already listed by the API and room-c belongs to another server — both dropped.
        assertEquals(
            listOf("room-b"),
            result.map { it.roomName },
        )
    }

    @Test
    fun `recentRoomsNotInApiList excludes rooms from other servers`() {
        val recent = listOf(
            RecentRoom("shared-room", "inst-2", "Server B"),
        )

        // The active server is inst-1, so a recent on inst-2 is never surfaced — even by the same name.
        val result = recentRoomsNotInApiList(recent, emptySet(), "inst-1")

        assertEquals(emptyList<String>(), result.map { it.roomName })
    }
}