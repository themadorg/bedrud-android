package com.bedrud.app.core.rooms

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

private const val DeletedAtMs = 1_000_000L

/** The server's async-delete window the tombstone has to outlast, ten minutes. */
private const val WindowMs = 10 * 60_000L

/**
 * How long a just-deleted room stays hidden from a refresh. The tombstone is process-wide, so every
 * test uses a room id of its own.
 */
class DeletedRoomTombstonesTest {

    private fun freshRoomId() = "room-${UUID.randomUUID()}"

    @Test
    fun `should hide a room the moment it is deleted`() {
        val roomId = freshRoomId()
        DeletedRoomTombstones.add(roomId, now = DeletedAtMs)

        assertTrue(DeletedRoomTombstones.isTombstoned(roomId, now = DeletedAtMs))
    }

    @Test
    fun `should still hide it at the end of the window`() {
        val roomId = freshRoomId()
        DeletedRoomTombstones.add(roomId, now = DeletedAtMs)

        assertTrue(DeletedRoomTombstones.isTombstoned(roomId, now = DeletedAtMs + WindowMs))
    }

    @Test
    fun `should stop hiding it once the window has passed`() {
        val roomId = freshRoomId()
        DeletedRoomTombstones.add(roomId, now = DeletedAtMs)

        assertFalse(DeletedRoomTombstones.isTombstoned(roomId, now = DeletedAtMs + WindowMs + 1))
    }

    @Test
    fun `should not hide a room that was never deleted`() {
        assertFalse(DeletedRoomTombstones.isTombstoned(freshRoomId(), now = DeletedAtMs))
    }

    /** A room deleted again, after its first tombstone lapsed, is hidden for a whole new window. */
    @Test
    fun `should start the window again for a room deleted again`() {
        val roomId = freshRoomId()
        DeletedRoomTombstones.add(roomId, now = DeletedAtMs)
        val deletedAgainAtMs = DeletedAtMs + WindowMs + 1
        DeletedRoomTombstones.add(roomId, now = deletedAgainAtMs)

        assertTrue(DeletedRoomTombstones.isTombstoned(roomId, now = deletedAgainAtMs + WindowMs))
    }
}
