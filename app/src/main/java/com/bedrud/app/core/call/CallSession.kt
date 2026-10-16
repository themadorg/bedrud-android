package com.bedrud.app.core.call

import com.bedrud.app.core.livekit.ConnectionState
import com.bedrud.app.core.livekit.RoomManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * What a running call decides about its room, apart from the service that keeps it alive.
 *
 * [CallService] holds what only an Android service can: the foreground notification, the wake lock
 * and the telecom call. When to give up on the room, what to say when a moderator removes the user,
 * which mute to believe and what leaving does to the room are decided here, where a unit test can
 * reach them, and the service carries them out through [Host].
 */
internal class CallSession(
    private val roomManager: RoomManager,
    private val scope: CoroutineScope,
    private val host: Host,
) {

    /** What the session asks of the service hosting it. */
    interface Host {
        /** Ends the service without hanging up, so the room keeps its state to show why. */
        fun stop()

        /** Ends the service and the call with it. */
        fun hangUp()

        /** Shows the microphone as muted, or not, on the call notification. */
        fun showMuted(isMuted: Boolean)

        /** Says, where the user lands, that a moderator removed them from the call. */
        fun reportRemoval()

        /** The room is connected, so its audio route can be followed. Called again after a reconnect. */
        fun onConnected()
    }

    /** Joins the room, and starts following it until [end]. */
    fun start(url: String, token: String, roomName: String, avatarUrl: String?) {
        scope.launch {
            roomManager.connectIfNeeded(url, token, roomName, avatarUrl)
        }

        // Tear the foreground service + ongoing-call notification down the moment the connection
        // fails (e.g. the media server's TLS certificate is untrusted), instead of leaving it up
        // for a call that never connected. Stopping deliberately does NOT hang up, so RoomManager
        // stays FAILED and the meeting screen keeps showing the error until the user leaves.
        scope.launch {
            roomManager.connectionState.collectLatest { state ->
                if (state == ConnectionState.FAILED) host.stop()
            }
        }

        roomManager.onDisconnected = {
            // Said where the user lands, as a failed join is: the meeting screen leaves the moment
            // the call ends, and hanging up resets the removal flag this reads.
            if (roomManager.wasKicked.value) host.reportRemoval()
            host.hangUp()
        }

        scope.launch {
            roomManager.isMicEnabled.collectLatest { micEnabled ->
                host.showMuted(!micEnabled)
            }
        }

        // Proximity blanking follows the audio route, and the route lives on the audio handler the
        // room only builds once it connects — so it binds on connection rather than here. Binding
        // is idempotent, which matters because a reconnect reports CONNECTED again.
        scope.launch {
            roomManager.connectionState.collectLatest { state ->
                if (state == ConnectionState.CONNECTED) host.onConnected()
            }
        }
    }

    /** A mute Telecom reports, from a headset, a car, or the system's own call controls. */
    fun onTelecomMute(muted: Boolean) {
        scope.launch {
            // Telecom fires an initial onMuteStateChanged(false) while the room is still
            // connecting; RoomManager's own connect flow already enables the mic by default,
            // so forwarding this early callback would race the LiveKit engine and surface a
            // spurious "failed to enable microphone" error. Only forward real mute changes
            // once we're actually connected.
            if (roomManager.connectionState.value == ConnectionState.CONNECTED) {
                roomManager.setMicrophoneEnabled(!muted)
            }
            host.showMuted(muted)
        }
    }

    /** Turns the microphone over, from the notification or the picture-in-picture window. */
    fun toggleMute() {
        scope.launch {
            roomManager.toggleMicrophone()
        }
    }

    /**
     * Stops following the room. It is disconnected only when [userHungUp]: a call that stopped on
     * its own, because the join failed, leaves the room in that state for the screen to show.
     */
    fun end(userHungUp: Boolean) {
        roomManager.onDisconnected = null
        if (userHungUp) roomManager.disconnect()
    }
}
