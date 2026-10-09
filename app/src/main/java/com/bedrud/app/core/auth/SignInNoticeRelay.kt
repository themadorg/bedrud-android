package com.bedrud.app.core.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Carries the reason the app signed the user out onto the sign-in screen they land on.
 *
 * Signing out sends the app to sign-in with the back stack cleared, so the screen that signed out
 * cannot say why itself: its snackbar host goes with it. Said here instead, the reason is picked up
 * by the sign-in screen, where the user actually is.
 *
 * Held outside composition (a singleton, like
 * [JoinFailureRelay][com.bedrud.app.core.rooms.JoinFailureRelay]) because the two ends are never
 * composed together.
 */
class SignInNoticeRelay {
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun report(message: String) {
        _message.value = message
    }

    /** Called once the message has been shown, so it isn't shown again. */
    fun consume() {
        _message.value = null
    }
}
