package com.bedrud.app.core.audio

/** How the microphone transmits in a call. */
enum class MeetingInputMode {
    /** Mic transmits continuously; the voice gate applies when sensitivity is manual. */
    VOICE_ACTIVITY,

    /** Mic transmits only while the talk control is held. */
    PUSH_TO_TALK,
}

/**
 * Capture noise suppression. Applies when a call connects (the audio device module is built
 * once per connection). RNNoise / Krisp are tracked in #106.
 */
enum class NoiseSuppressionMode {
    /** The device's built-in suppressor (hardware NS when available). */
    DEVICE,

    /** No suppression beyond what the platform always applies. */
    OFF,
}
