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
    val joinedAt: Long = System.currentTimeMillis(),
)

class RecentRoomsStore(private val prefs: SharedPreferences) {

    constructor(context: Context) : this(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
    )

    private val gson = Gson()

    private val _rooms = MutableStateFlow(loadRooms())
    val rooms: StateFlow<List<RecentRoom>> = _rooms.asStateFlow()

    fun add(roomName: String, instanceId: String, instanceName: String) {
        val trimmed = roomName.trim()
        if (trimmed.isBlank()) return

        val entry = RecentRoom(
            roomName = trimmed,
            instanceId = instanceId,
            instanceName = instanceName.ifBlank { instanceId },
            joinedAt = System.currentTimeMillis(),
        )
        val updated = listOf(entry) +
            _rooms.value.filterNot {
                it.roomName == trimmed && it.instanceId == instanceId
            }
        _rooms.value = updated.take(MAX_RECENT)
        saveRooms(updated.take(MAX_RECENT))
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

fun recentRoomsNotInApiList(
    recentRooms: List<RecentRoom>,
    apiRoomNames: Set<String>,
    activeInstanceId: String?,
): List<RecentRoom> =
    recentRooms.filter { recent ->
        recent.instanceId != activeInstanceId || recent.roomName !in apiRoomNames
    }

fun formatRecentRoomTimeAgo(joinedAt: Long, now: Long = System.currentTimeMillis()): String {
    val seconds = ((now - joinedAt) / 1000).coerceAtLeast(0)
    return when {
        seconds < 60 -> "now"
        seconds < 3600 -> "${seconds / 60}m"
        seconds < 86_400 -> "${seconds / 3600}h"
        seconds < 604_800 -> "${seconds / 86_400}d"
        else -> "${seconds / 604_800}w"
    }
}