package com.bedrud.app.core.rooms

/**
 * Recently deleted room ids, remembered for the server's async-delete window.
 *
 * DELETE room/{id} returns 202: the server only queues the deletion, and `room/list` keeps
 * returning the room — first with no `deletedAt` stamp at all, then stamped (and stamped rooms
 * are filtered out by the client). This bridge covers the unstamped window so a just-deleted
 * room can't resurface on a refresh.
 *
 * Process-lifetime by design (a plain object, not composition state): the list screen is torn
 * down and rebuilt on every tab switch or navigation, which is exactly when a composition-scoped
 * tombstone would be lost and the room would reappear.
 */
object DeletedRoomTombstones {
    private const val TTL_MS = 10 * 60_000L

    private val deletedAtMsByRoomId = mutableMapOf<String, Long>()

    @Synchronized
    fun add(roomId: String) {
        deletedAtMsByRoomId[roomId] = System.currentTimeMillis()
    }

    @Synchronized
    fun isTombstoned(roomId: String): Boolean {
        val now = System.currentTimeMillis()
        deletedAtMsByRoomId.entries.removeAll { now - it.value > TTL_MS }
        return roomId in deletedAtMsByRoomId
    }
}
