package com.bedrud.app.core.call

/**
 * Answers which of Telecom's audio-state callbacks carry a mute change the app should apply.
 *
 * Below API 34 a headset's or car's mute reaches a self-managed connection only inside
 * `CallAudioState`, which Telecom also re-sends for every routing change, carrying the mute it
 * carried before. Passing each one on would apply the same mute again on every route change, so
 * only a mute that differs from the last one seen is passed on.
 *
 * From API 34 `Connection.onMuteStateChanged` reports every mute change on its own; with
 * [reportsMuteSeparately] set, nothing is passed on here, so no change is applied twice.
 *
 * Not thread-safe: it belongs to the connection that owns it, whose callbacks arrive in order.
 */
class CallAudioStateMuteFilter(private val reportsMuteSeparately: Boolean) {

    private var lastMuted: Boolean? = null

    /**
     * Returns the mute state to apply, or null when this callback carries no change to it.
     */
    fun onAudioStateChanged(isMuted: Boolean): Boolean? {
        if (reportsMuteSeparately || isMuted == lastMuted) return null
        lastMuted = isMuted
        return isMuted
    }
}
