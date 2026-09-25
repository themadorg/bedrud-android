package com.bedrud.app.core.rooms

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A room the app has been asked to open, and the server it was asked for on.
 *
 * The server matters because room names are only unique per server: a room held for one server
 * must never be opened on another that happens to be active by the time it could open.
 */
data class RoomRequest(val roomName: String, val serverId: String?)

/**
 * Holds the one room the app has been asked to open until somebody is signed in to open it.
 *
 * Every way into a meeting — a room card, a pasted or deep link, the call notification — can arrive
 * while the server it belongs to has nobody signed in, most often right after a switch to another
 * server. The sign-in that follows clears the back stack, so a room kept only as a navigation entry
 * is lost on the way; held here, it is opened once the sign-in lands.
 *
 * A room is dropped rather than opened once another server becomes active, since the reader has
 * gone somewhere else, and it is handed over only once, so returning to the dashboard afterwards
 * does not pull the reader back into it.
 */
class PendingRoom {
    private val _request = MutableStateFlow<RoomRequest?>(null)
    val request: StateFlow<RoomRequest?> = _request.asStateFlow()

    /** Holds [roomName] on [serverId] in place of any room held before it. */
    fun hold(roomName: String, serverId: String?) {
        _request.value = RoomRequest(roomName, serverId)
    }

    /**
     * Returns the held room when it can be opened now, and forgets it, or null when there is none.
     *
     * A room stays held while its server has nobody signed in, and is dropped once another server
     * is active.
     */
    fun take(activeServerId: String?, isSignedIn: Boolean): String? {
        val request = _request.value ?: return null
        if (request.serverId != activeServerId) {
            _request.value = null
            return null
        }
        if (!isSignedIn) return null
        _request.value = null
        return request.roomName
    }
}
