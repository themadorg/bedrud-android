package com.bedrud.app.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing

/**
 * Motion tokens — durations + easing for UI transitions (selection, expand/collapse, emphasis),
 * so animations stay consistent instead of each call site inventing its own timing.
 *
 * Use with `tween(Motion.durationMedium, easing = Motion.standardEasing)` in `animate*AsState`.
 */
object Motion {
    const val durationShort = 150
    const val durationMedium = 250
    const val durationLong = 400

    /** Idle time before the in-call fullscreen chrome hides itself. */
    const val meetingChromeAutoHideDelayMs = 3500L

    /** How long the top bar says "Connected" before handing the slot back to the room name. */
    const val meetingConnectedNoticeMs = 2000L

    /**
     * How long the lightbox says whether a picture was saved before handing the screen back to the
     * picture, which is the only reason anyone opened it.
     */
    const val lightboxOutcomeNoticeMs = 2500L

    /**
     * How long every speaking signal takes to arrive and to settle — the tile ring's fade and the
     * name chip's badge alike, so the two never disagree about when someone started talking.
     *
     * Short on purpose: it is pure delay on both ends, and bridging the gaps between server
     * speaker reports is `SpeakingTracker`'s job, not the animation's.
     */
    const val meetingSpeakingFadeMs = 110

    /** Speaking-ring thickness follows the reported level faster than the ring fades. */
    const val meetingSpeakingRingLevelMs = 90

    /** One lap of the arc travelling round the mic pill while reconnecting. */
    const val meetingMicRingTravelMs = 1400

    /** Half a pulse of the mic pill's ring for a cause you can fix yourself. */
    const val meetingMicRingPulseMs = 700

    /**
     * How long the mic pill's ring takes to arrive and to leave.
     *
     * Longer than the speaking ring's fade: that one tracks a voice and wants to feel immediate,
     * while this one reports a problem and should ease in and out rather than blink at you.
     */
    const val meetingMicRingFadeMs = 240

    val standardEasing = FastOutSlowInEasing
}
