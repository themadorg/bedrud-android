package com.bedrud.app.core.rooms

import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/**
 * Reads one of the server's RFC 3339 timestamps as epoch milliseconds.
 *
 * Returns null whenever the value cannot be trusted — absent, blank, or in a shape this client
 * does not know. A server the app has never met before must leave a room card plainer than
 * intended, never crash the list that draws it.
 */
fun parseServerTimestamp(value: String?): Long? {
    val text = value?.trim()
    if (text.isNullOrEmpty()) return null
    return try {
        // Accepts both the trailing "Z" and a numeric offset, and truncates Go's nanoseconds.
        OffsetDateTime.parse(text).toInstant().toEpochMilli()
    } catch (parseFailure: DateTimeParseException) {
        null
    }
}

/**
 * When a room was last active, in epoch milliseconds, or null when nobody knows.
 *
 * The server's own record wins: it counts every participant's join, while this device remembers
 * only the visits made from it. Local history is the fallback for a server that does not report
 * activity, and for a room whose card is drawn while offline.
 */
fun resolveRoomActivityAt(serverLastActivityAt: String?, localVisitAtMs: Long?): Long? =
    parseServerTimestamp(serverLastActivityAt) ?: localVisitAtMs

/**
 * Orders items by when they were last active, most recent first, with the ones nobody can date
 * left at the end in the order they arrived.
 *
 * The sort is stable on purpose. Rooms sharing a timestamp, and the undated tail, keep the order
 * the caller handed over — otherwise the list reshuffles under the reader on every refresh.
 */
fun <T> sortByActivity(items: List<T>, activityAtMs: (T) -> Long?): List<T> {
    val (dated, undated) = items.partition { item -> activityAtMs(item) != null }
    return dated.sortedByDescending { item -> activityAtMs(item) } + undated
}
