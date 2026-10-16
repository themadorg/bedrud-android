package com.bedrud.app.core.rooms

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Carries the reason a join failed out of the meeting screen and onto the screen the user lands on.
 *
 * A failed join has nothing to show and nothing to wait for, so the meeting screen leaves at once —
 * which is exactly why it cannot say anything itself: its own snackbar host goes with it. Said here
 * instead, the message is picked up by the main screen once the pop lands, so the explanation
 * arrives where the user actually is.
 *
 * Held outside composition (a singleton, like [DeletedRoomTombstones]) because the two ends are
 * never composed together: the meeting screen is being torn down at the moment it reports.
 *
 * A call a moderator ends, by kicking or banning, is reported here too, by `CallService`: that
 * meeting screen leaves just as abruptly, and the call may be running with no screen open at all.
 */
class JoinFailureRelay {
    /** What to say, and whether it is a removal rather than a failure, which reads as a warning. */
    data class Notice(val message: String, val isRemoval: Boolean)

    private val _message = MutableStateFlow<Notice?>(null)
    val message: StateFlow<Notice?> = _message.asStateFlow()

    fun report(message: String, isRemoval: Boolean = false) {
        _message.value = Notice(message, isRemoval)
    }

    /** Called once the message has been shown, so it isn't shown again. */
    fun consume() {
        _message.value = null
    }
}
