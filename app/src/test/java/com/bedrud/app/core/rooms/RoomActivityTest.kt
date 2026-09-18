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

    @Test
    fun `sortByActivity puts the most recently active first`() {
        val items = listOf("older" to 1_000L, "newest" to 3_000L, "middle" to 2_000L)

        val sorted = sortByActivity(items) { (_, activityAtMs) -> activityAtMs }

        assertEquals(listOf("newest", "middle", "older"), sorted.map { (name, _) -> name })
    }

    @Test
    fun `sortByActivity keeps items of unknown activity last, in the order they arrived`() {
        val items = listOf(
            "unknown-first" to null,
            "active" to 1_000L,
            "unknown-second" to null,
        )

        val sorted = sortByActivity(items) { (_, activityAtMs) -> activityAtMs }

        // The server's own order is the only thing left to rank a room nobody has ever been in.
        assertEquals(
            listOf("active", "unknown-first", "unknown-second"),
            sorted.map { (name, _) -> name },
        )
    }

    @Test
    fun `sortByActivity leaves equally active items in the order they arrived`() {
        val items = listOf("first" to 1_000L, "second" to 1_000L, "third" to 1_000L)

        val sorted = sortByActivity(items) { (_, activityAtMs) -> activityAtMs }

        // A stable sort matters here: an unstable one would reshuffle the list on every refresh.
        assertEquals(listOf("first", "second", "third"), sorted.map { (name, _) -> name })
    }

    @Test
    fun `sortByActivity returns an empty list unchanged`() {
        assertEquals(emptyList<Pair<String, Long?>>(), sortByActivity(emptyList<Pair<String, Long?>>()) { null })
    }
}
