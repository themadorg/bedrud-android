package com.bedrud.app.core.rooms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoomActivityTest {

    @Test
    fun `parseServerTimestamp reads an RFC3339 instant in UTC`() {
        assertEquals(
            1_758_189_600_000L,
            parseServerTimestamp("2025-09-18T10:00:00Z"),
        )
    }

    @Test
    fun `parseServerTimestamp keeps millisecond precision from Go's nanosecond output`() {
        // Go marshals time.Time with up to nanosecond precision; everything below a
        // millisecond is truncated rather than rounded.
        assertEquals(
            1_758_189_600_123L,
            parseServerTimestamp("2025-09-18T10:00:00.123456789Z"),
        )
    }

    @Test
    fun `parseServerTimestamp honours a numeric UTC offset`() {
        // Same instant as the UTC case above, written from a +03:30 zone.
        assertEquals(
            1_758_189_600_000L,
            parseServerTimestamp("2025-09-18T13:30:00+03:30"),
        )
    }

    @Test
    fun `parseServerTimestamp returns null when the server omits the field`() {
        assertNull(parseServerTimestamp(null))
    }

    @Test
    fun `parseServerTimestamp returns null for text it cannot read`() {
        // A server that is older, newer, or simply different must never crash the list.
        assertNull(parseServerTimestamp("last tuesday"))
    }

    @Test
    fun `parseServerTimestamp returns null for a blank field`() {
        assertNull(parseServerTimestamp("   "))
    }

    @Test
    fun `resolveRoomActivityAt prefers the server's record over this device's history`() {
        // The card reports when the room was last active, which is anybody's visit —
        // not only the ones this device happens to remember.
        assertEquals(
            1_758_189_600_000L,
            resolveRoomActivityAt(
                serverLastActivityAt = "2025-09-18T10:00:00Z",
                localVisitAtMs = 1_700_000_000_000L,
            ),
        )
    }

    @Test
    fun `resolveRoomActivityAt falls back to this device's history when the server is silent`() {
        assertEquals(
            1_700_000_000_000L,
            resolveRoomActivityAt(serverLastActivityAt = null, localVisitAtMs = 1_700_000_000_000L),
        )
    }

    @Test
    fun `resolveRoomActivityAt falls back to this device's history when the server sends nonsense`() {
        assertEquals(
            1_700_000_000_000L,
            resolveRoomActivityAt(
                serverLastActivityAt = "not a timestamp",
                localVisitAtMs = 1_700_000_000_000L,
            ),
        )
    }

    @Test
    fun `resolveRoomActivityAt returns null when neither side knows`() {
        assertNull(resolveRoomActivityAt(serverLastActivityAt = null, localVisitAtMs = null))
    }
}
