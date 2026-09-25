package com.bedrud.app.core.recent

import com.bedrud.app.testutil.InMemorySharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

class RecentRoomsStoreTest {

    // The preference key the store keeps its list under, so a test can seed what an older version
    // of the app wrote there.
    private val storedRoomsKey = "rooms"

    @Test
    fun `should load a recent saved with the server name and colour it used to keep`() {
        val prefs = InMemorySharedPreferences()
        prefs.edit().putString(
            storedRoomsKey,
            """[{"roomName":"room-a","instanceId":"inst-1","instanceName":"Server A",""" +
                """"instanceColorHex":"#3B82F6","joinedAt":1000,"leftAt":2000}]""",
        ).apply()

        val loaded = RecentRoomsStore(prefs).rooms.value.single()

        assertEquals("room-a", loaded.roomName)
        assertEquals("inst-1", loaded.instanceId)
        assertEquals(1000L, loaded.joinedAt)
        assertEquals(2000L, loaded.leftAt)
    }

    @Test
    fun `recentRoomsNotInApiList keeps active-server rooms not already in the API list`() {
        val recent = listOf(
            RecentRoom("room-a", "inst-1"),
            RecentRoom("room-b", "inst-1"),
            RecentRoom("room-c", "inst-2"),
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
            RecentRoom("shared-room", "inst-2"),
        )

        // The active server is inst-1, so a recent on inst-2 is never surfaced — even by the same name.
        val result = recentRoomsNotInApiList(recent, emptySet(), "inst-1")

        assertEquals(emptyList<String>(), result.map { it.roomName })
    }
}