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

    /**
     * How long the controls bar takes to grow into the options panel, and to fall back.
     *
     * Owned here rather than taken from the platform sheet this replaced: `ModalBottomSheet` hides
     * on Material's `FastEffects`, which starts at full speed with no ease-in and was gone in
     * 117 ms — measured against a 250 ms eased open, so the panel left twice as fast as it
     * arrived and read as being yanked away. These two are close enough to feel like one gesture
     * reversed, with the collapse a little quicker because a panel on its way out should not keep
     * the call waiting.
     */
    const val meetingOptionsExpandMs = 320
    const val meetingOptionsCollapseMs = 260

    /**
     * The bar's contents trading places: call controls out, chat composer in — and back.
     *
     * Slot-level, not a whole-bar crossfade: the camera key hands its corner to the "+", the mic
     * pill hands the middle to the field, hang-up hands its end to send. Shorter than the panel
     * growing above it (the controls are light elements, the panel is a heavy one), and the close
     * quicker than the open for the same reason the options panel's is: what is leaving should
     * not keep the call waiting.
     */
    const val meetingChatMorphOpenMs = 280
    const val meetingChatMorphCloseMs = 220

    /**
     * The conversation growing out of the bar, and folding back into it.
     *
     * The options panel's own timings, stretched: this panel travels roughly twice the height, and
     * a taller reveal on the same clock reads faster than the small one, not the same. Slightly
     * longer keeps the perceived pace of the two panels equal.
     */
    const val meetingChatExpandMs = 360
    const val meetingChatCollapseMs = 300

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
