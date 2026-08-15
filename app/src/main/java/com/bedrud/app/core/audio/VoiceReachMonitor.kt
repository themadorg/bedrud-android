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
 * Every verdict needs the speech to persist, because a single loud frame is a cough, not a
 * sentence. [ReachGraceMillis] is the longer wait: it has to outlast a server report cycle plus
 * the round trip before silence from the room means anything.
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

    private var talkingSinceMillis: Long? = null
    private var lastLoudAtMillis: Long? = null
    private var roomLastHeardMillis: Long? = null

    /**
     * Folds one sample of the local capture level and the room's view of it into a verdict.
     *
     * [micLevel] must go quiet honestly when capture stops — a frozen last reading would look like
     * speech forever, which is exactly what happens on mute.
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
        if (!talking) {
            talkingSinceMillis = null
            return MeetingVoiceAlert.None
        }

        val talkingSince = talkingSinceMillis ?: nowMillis.also { talkingSinceMillis = it }
        val talkingFor = nowMillis - talkingSince

        return when {
            !isMicEnabled && isPushToTalk -> after(talkingFor, causeGraceMillis, MeetingVoiceAlert.PushToTalkIdle)
            !isMicEnabled -> after(talkingFor, causeGraceMillis, MeetingVoiceAlert.Muted)
            !isGateOpen -> after(talkingFor, causeGraceMillis, MeetingVoiceAlert.GateClosed)
            // Alone in the room there is nobody to not hear you, and the server has no reason to
            // report a speaker to an empty room — so silence from it proves nothing and the
            // warning would be both false and nonsense. Local causes above still apply.
            !roomHasOthers -> MeetingVoiceAlert.None
            else -> {
                val heardRecently = roomLastHeardMillis?.let { nowMillis - it < reachGraceMillis } == true
                if (heardRecently) {
                    MeetingVoiceAlert.None
                } else {
                    after(talkingFor, reachGraceMillis, MeetingVoiceAlert.NotReachingRoom)
                }
            }
        }
    }

    fun reset() {
        talkingSinceMillis = null
        lastLoudAtMillis = null
        roomLastHeardMillis = null
    }

    private fun after(talkingFor: Long, graceMillis: Long, alert: MeetingVoiceAlert) =
        if (talkingFor >= graceMillis) alert else MeetingVoiceAlert.None

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
