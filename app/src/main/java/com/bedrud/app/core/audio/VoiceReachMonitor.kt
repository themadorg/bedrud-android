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
 * [QuietHoldMillis] past the last sample of speech — long enough to bridge the gaps inside a
 * sentence, short enough to end when you stop. Whether a sample is speech at all is
 * [SpeechLevelTracker]'s call, made against this microphone's own noise floor.
 *
 * That floor is this device's, but the room's reports are not: the server names a speaker only
 * above a fixed level of its own, [RoomSpeakerLevel]. Speech that clears the floor but not that
 * level is still worth a local warning — muted is muted however quietly you speak — yet the room
 * staying silent about it proves nothing, so the room is only blamed for speech it would report.
 */
class VoiceReachMonitor(
    private val causeGraceMillis: Long = CauseGraceMillis,
    private val reachGraceMillis: Long = ReachGraceMillis,
    private val quietHoldMillis: Long = QuietHoldMillis,
    private val roomSpeakerLevel: Float = RoomSpeakerLevel,
) {

    private var lastLoudAtMillis: Long? = null
    private var lastReportableAtMillis: Long? = null
    private var roomLastHeardMillis: Long? = null
    private var cause: MeetingVoiceAlert = MeetingVoiceAlert.None
    private var causeSinceMillis: Long? = null

    /**
     * Folds one sample of the local capture and the room's view of it into a verdict.
     *
     * [isSpeech] must go false honestly when capture stops — a frozen last reading would look like
     * speech forever. [micLevel] is that same sample's level, null once capture has stalled, and
     * only decides whether the room would have reported it.
     */
    fun sample(
        nowMillis: Long,
        micLevel: Float?,
        isSpeech: Boolean,
        isMicEnabled: Boolean,
        isPushToTalk: Boolean,
        isGateOpen: Boolean,
        roomHearsMe: Boolean,
        roomHasOthers: Boolean,
    ): MeetingVoiceAlert {
        if (roomHearsMe) roomLastHeardMillis = nowMillis
        if (isSpeech) lastLoudAtMillis = nowMillis
        if (isSpeech && micLevel != null && micLevel >= roomSpeakerLevel) {
            lastReportableAtMillis = nowMillis
        }

        // Speech is loud in bursts with gaps between words, so "talking" has to survive a dip or
        // the run never lasts long enough to judge — and would never end once it had. Talking
        // loudly enough for the room to report rides the same hold, for the same reason.
        val lastLoud = lastLoudAtMillis
        val talking = lastLoud != null && nowMillis - lastLoud < quietHoldMillis
        val talkingReportably = lastReportableAtMillis
            ?.let { nowMillis - it < quietHoldMillis } == true

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
            // Too quiet for the server to name as a speaker, so its silence is expected.
            !talkingReportably -> MeetingVoiceAlert.None
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
        lastReportableAtMillis = null
        roomLastHeardMillis = null
        cause = MeetingVoiceAlert.None
        causeSinceMillis = null
    }

    private fun after(heldFor: Long, graceMillis: Long, alert: MeetingVoiceAlert) =
        if (heldFor >= graceMillis) alert else MeetingVoiceAlert.None

    companion object {
        /**
         * The level, on the capture scale, below which the server does not name a speaker.
         *
         * LiveKit's `audio.active_level` defaults to 30, meaning -30 dBov, which lands at 0.5 on a
         * scale running linearly from -60 to 0 dB. The server also wants that level held for more
         * than 40% of each interval; that is not modelled here, and [QuietHoldMillis] bridges the
         * same gaps instead. A deployment can change both, and the client is never told, so this
         * assumes the default. Measured on device with a second participant, a hum between 0.15
         * and 0.5 was never reported by the room, and blaming the room for it lit the warning on a
         * call that was working.
         */
        const val RoomSpeakerLevel = 0.5f

        /**
         * How long a locally-known cause (muted, gate shut) must hold before it is worth saying.
         *
         * Only long enough to tell a word from a cough. These causes are known on the device the
         * instant they happen — there is nothing to wait for but confidence that you meant to
         * speak — so the wait is short and the ring feels like it answers you.
         */
        const val CauseGraceMillis = 300L

        /** Talking time with no word from the room before the audio is treated as not arriving. */
        const val ReachGraceMillis = 2_500L

        /**
         * How long a sample that is not speech still counts as talking.
         *
         * Long enough to ride the gap between two words, short enough that the ring goes out
         * promptly when you actually stop. Ordinary speech gaps run 150-300ms.
         */
        const val QuietHoldMillis = 400L

        /**
         * How often the local capture level is compared against the room's view of it.
         *
         * This is the floor on how fast the ring can react, and the work per tick is a volatile
         * read and a handful of comparisons, so it is set by what feels immediate rather than by
         * what is cheap. Capture frames arrive every 10ms; sampling five times slower than that
         * is still far below the point where the delay is visible.
         */
        const val SampleIntervalMillis = 50L
    }
}
