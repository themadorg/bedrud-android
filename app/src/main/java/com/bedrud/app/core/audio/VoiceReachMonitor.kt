package com.bedrud.app.core.audio

/**
 * Why the room is not hearing someone who is clearly talking. Each value names a different cause
 * with a different fix, so the UI can say something more useful than "check your audio".
 */
enum class MeetingVoiceAlert {
    /** Nothing wrong, or not talking. */
    None,

    /** Talking with the microphone switched off. */
    Muted,

    /** Talking in push-to-talk mode without holding the button. */
    PushToTalkIdle,

    /** Talking, but the manual voice gate is holding the audio back — sensitivity is too high. */
    GateClosed,

    /** Talking into a live microphone that the room never reports hearing. */
    NotReachingRoom,
}

/**
 * Compares what the microphone captures against what the room says it hears, and names the gap.
 *
 * The in-call mic meter is drawn from local capture alone, so it happily bounces while a broken
 * publish sends nothing at all — the exact failure people only discover when someone finally says
 * "we can't hear you". The room's own speaker reports close that loop: sustained local speech
 * that the room never echoes back means the audio is not arriving, whatever the meter shows.
 *
 * Every verdict needs its cause to persist, because a cause that comes and goes in an instant is
 * a state change, not a fault — the mic flipping during an unmute tap, or the gate riding its
 * hangover between two words. [ReachGraceMillis] is the longer wait: it has to outlast a server
 * report cycle plus the round trip before silence from the room means anything.
 *
 * Nothing is ever reported while you are quiet. The whole point is to answer "am I talking to
 * nobody", which is only a question while you are talking, so talking is treated as lasting
 * [QuietHoldMillis] past the last loud frame — long enough to bridge the gaps inside a sentence,
 * short enough to end when you stop.
 */
class VoiceReachMonitor(
    private val talkingLevel: Float = TalkingLevel,
    private val causeGraceMillis: Long = CauseGraceMillis,
    private val reachGraceMillis: Long = ReachGraceMillis,
    private val quietHoldMillis: Long = QuietHoldMillis,
) {

    private var lastLoudAtMillis: Long? = null
    private var roomLastHeardMillis: Long? = null
    private var cause: MeetingVoiceAlert = MeetingVoiceAlert.None
    private var causeSinceMillis: Long? = null

    /**
     * Folds one sample of the local capture level and the room's view of it into a verdict.
     *
     * [micLevel] must go quiet honestly when capture stops — a frozen last reading would look like
     * speech forever.
     */
    fun sample(
        nowMillis: Long,
        micLevel: Float,
        isMicEnabled: Boolean,
        isPushToTalk: Boolean,
        isGateOpen: Boolean,
        roomHearsMe: Boolean,
        roomHasOthers: Boolean,
    ): MeetingVoiceAlert {
        if (roomHearsMe) roomLastHeardMillis = nowMillis
        if (micLevel >= talkingLevel) lastLoudAtMillis = nowMillis

        // Speech is loud in bursts with gaps between words, so "talking" has to survive a dip or
        // the run never lasts long enough to judge — and would never end once it had.
        val lastLoud = lastLoudAtMillis
        val talking = lastLoud != null && nowMillis - lastLoud < quietHoldMillis

        // The clock only runs while you are talking. Letting it accumulate through a silence
        // meant the first word after a long pause arrived with the grace already served, and
        // flashed before the server had any chance to report that word.
        if (!talking) {
            cause = MeetingVoiceAlert.None
            causeSinceMillis = null
            return MeetingVoiceAlert.None
        }

        val heardRecently = roomLastHeardMillis?.let { nowMillis - it < reachGraceMillis } == true
        val current = when {
            !isMicEnabled && isPushToTalk -> MeetingVoiceAlert.PushToTalkIdle
            !isMicEnabled -> MeetingVoiceAlert.Muted
            !isGateOpen -> MeetingVoiceAlert.GateClosed
            // Alone in the room there is nobody to not hear you, and the server has no reason to
            // report a speaker to an empty room — so silence from it proves nothing.
            !roomHasOthers -> MeetingVoiceAlert.None
            heardRecently -> MeetingVoiceAlert.None
            else -> MeetingVoiceAlert.NotReachingRoom
        }

        // The wait belongs to the cause, not to the talking. Timing it from when speech started
        // meant that once you had been talking a while, any momentary blip — the mic state during
        // an unmute tap, the gate riding its hangover between words — had already served the
        // grace and flashed the ring on arrival.
        if (current != cause) {
            cause = current
            causeSinceMillis = nowMillis
        }
        if (current == MeetingVoiceAlert.None) return MeetingVoiceAlert.None

        val heldFor = causeSinceMillis?.let { nowMillis - it } ?: 0L
        val grace = if (current == MeetingVoiceAlert.NotReachingRoom) {
            reachGraceMillis
        } else {
            causeGraceMillis
        }
        return after(heldFor, grace, current)
    }

    fun reset() {
        lastLoudAtMillis = null
        roomLastHeardMillis = null
        cause = MeetingVoiceAlert.None
        causeSinceMillis = null
    }

    private fun after(heldFor: Long, graceMillis: Long, alert: MeetingVoiceAlert) =
        if (heldFor >= graceMillis) alert else MeetingVoiceAlert.None

    companion object {
        /**
         * Capture level that counts as talking, on [VoiceGateProcessor]'s normalized scale.
         *
         * Measured on device once the capture format was decoded correctly: a room with someone
         * talking in it sits around 0.25 between words and peaks near 0.84 on speech. This sits
         * between the two, high enough that room tone never reaches it and low enough that a
         * quiet speaker still does.
         */
        const val TalkingLevel = 0.5f

        /** Talking time before a locally-known cause (muted, gate shut) is worth saying out loud. */
        const val CauseGraceMillis = 800L

        /** Talking time with no word from the room before the audio is treated as not arriving. */
        const val ReachGraceMillis = 2_500L

        /** How long a dip below [TalkingLevel] still counts as talking — one pause between words. */
        const val QuietHoldMillis = 700L

        /** How often the local capture level is compared against the room's view of it. */
        const val SampleIntervalMillis = 200L
    }
}
