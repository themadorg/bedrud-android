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
 */
class VoiceReachMonitor(
    private val talkingLevel: Float = TalkingLevel,
    private val causeGraceMillis: Long = CauseGraceMillis,
    private val reachGraceMillis: Long = ReachGraceMillis,
) {

    private var talkingSinceMillis: Long? = null
    private var roomLastHeardMillis: Long? = null

    /**
     * Folds one sample of the local capture level and the room's view of it into a verdict.
     * [micLevel] is the raw capture level, which keeps reading while muted — that is what lets a
     * muted participant be told they are talking to nobody.
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

        if (micLevel < talkingLevel) {
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
        roomLastHeardMillis = null
    }

    private fun after(talkingFor: Long, graceMillis: Long, alert: MeetingVoiceAlert) =
        if (talkingFor >= graceMillis) alert else MeetingVoiceAlert.None

    companion object {
        /**
         * Capture level that counts as talking, on [VoiceGateProcessor]'s normalized scale.
         *
         * Tuned on device: at 0.4 (about -36 dBFS) room tone alone cleared the bar and raised the
         * warning with nobody speaking, because the phone's own gain lifts ambient well above what
         * the server's speaker detection reacts to. A warning that cries wolf gets ignored, and a
         * missed one costs only the warning, so the bar sits where speech is unambiguous.
         */
        const val TalkingLevel = 0.6f

        /** Talking time before a locally-known cause (muted, gate shut) is worth saying out loud. */
        const val CauseGraceMillis = 800L

        /** Talking time with no word from the room before the audio is treated as not arriving. */
        const val ReachGraceMillis = 2_500L

        /** How often the local capture level is compared against the room's view of it. */
        const val SampleIntervalMillis = 200L
    }
}
