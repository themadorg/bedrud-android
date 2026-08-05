package com.bedrud.app.core.recent

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RecentRoom(
    val roomName: String,
    val instanceId: String,
    val instanceName: String,
    // The server's accent color (`#RRGGBB`), captured at join time so a cross-server card stays
    // correctly tinted even after that instance is removed. Nullable for entries persisted before
    // this field existed — the UI falls back to a live instance lookup, then a neutral color.
    val instanceColorHex: String? = null,
    val joinedAt: Long = System.currentTimeMillis(),
    val leftAt: Long? = null,
)

class RecentRoomsStore(private val prefs: SharedPreferences) {

    constructor(context: Context) : this(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
    )

    private val gson = Gson()

    private val _rooms = MutableStateFlow(loadRooms())
    val rooms: StateFlow<List<RecentRoom>> = _rooms.asStateFlow()

    fun add(
        roomName: String,
        instanceId: String,
        instanceName: String,
        instanceColorHex: String? = null,
    ) {
        val trimmed = roomName.trim()
        if (trimmed.isBlank()) return

        val entry = RecentRoom(
            roomName = trimmed,
            instanceId = instanceId,
            instanceName = instanceName.ifBlank { instanceId },
            instanceColorHex = instanceColorHex,
            joinedAt = System.currentTimeMillis(),
        )
        val updated = listOf(entry) +
            _rooms.value.filterNot {
                it.roomName == trimmed && it.instanceId == instanceId
            }
        _rooms.value = updated.take(MAX_RECENT)
        saveRooms(updated.take(MAX_RECENT))
    }

    fun markLeft(roomName: String, instanceId: String) {
        val updated = _rooms.value.map {
            if (it.roomName == roomName && it.instanceId == instanceId) {
                it.copy(leftAt = System.currentTimeMillis())
            } else {
                it
            }
        }
        _rooms.value = updated
        saveRooms(updated)
    }

    fun remove(roomName: String, instanceId: String) {
        val updated = _rooms.value.filterNot {
            it.roomName == roomName && it.instanceId == instanceId
        }
        _rooms.value = updated
        saveRooms(updated)
    }

    fun clear() {
        _rooms.value = emptyList()
        prefs.edit().remove(KEY_ROOMS).apply()
    }

    private fun saveRooms(rooms: List<RecentRoom>) {
        prefs.edit().putString(KEY_ROOMS, gson.toJson(rooms)).apply()
    }

    private fun loadRooms(): List<RecentRoom> {
        val json = prefs.getString(KEY_ROOMS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<RecentRoom>>() {}.type
            gson.fromJson<List<RecentRoom>>(json, type).orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
    }

    companion object {
        private const val PREFS_NAME = "bedrud_recent_rooms"
        private const val KEY_ROOMS = "rooms"
        private const val MAX_RECENT = 20
    }
}

// Recent rooms on the active server that aren't already in its API room list — i.e. rooms joined
// by link on this server that don't surface as owned/listed rooms. Recents from other servers are
// intentionally excluded: the dashboard shows only the active server and nothing else.
fun recentRoomsNotInApiList(
    recentRooms: List<RecentRoom>,
    apiRoomNames: Set<String>,
    activeInstanceId: String?,
): List<RecentRoom> =
    recentRooms.filter { recent ->
        recent.instanceId == activeInstanceId && recent.roomName !in apiRoomNames
    }

private const val MILLIS_PER_SECOND = 1000L
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3600L
private const val SECONDS_PER_DAY = 86_400L
private const val SECONDS_PER_WEEK = 604_800L

fun formatRecentRoomTimeAgo(joinedAt: Long, now: Long = System.currentTimeMillis()): String {
    val seconds = ((now - joinedAt) / MILLIS_PER_SECOND).coerceAtLeast(0)
    return when {
        seconds < SECONDS_PER_MINUTE -> "now"
        seconds < SECONDS_PER_HOUR -> "${seconds / SECONDS_PER_MINUTE}m"
        seconds < SECONDS_PER_DAY -> "${seconds / SECONDS_PER_HOUR}h"
        seconds < SECONDS_PER_WEEK -> "${seconds / SECONDS_PER_DAY}d"
        else -> "${seconds / SECONDS_PER_WEEK}w"
    }
}