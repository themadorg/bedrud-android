package com.bedrud.app.core.pip

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PipStateHolder {
    private val _isInPipMode = MutableStateFlow(false)
    val isInPipMode: StateFlow<Boolean> = _isInPipMode.asStateFlow()

    private val _isInMeeting = MutableStateFlow(false)
    val isInMeeting: StateFlow<Boolean> = _isInMeeting.asStateFlow()

    // Whether the call's microphone is open, for the window's mute control to show.
    private val _isMicEnabled = MutableStateFlow(false)
    val isMicEnabled: StateFlow<Boolean> = _isMicEnabled.asStateFlow()

    fun setInPipMode(value: Boolean) {
        _isInPipMode.value = value
    }

    fun setInMeeting(value: Boolean) {
        _isInMeeting.value = value
    }

    fun setMicEnabled(value: Boolean) {
        _isMicEnabled.value = value
    }
}
