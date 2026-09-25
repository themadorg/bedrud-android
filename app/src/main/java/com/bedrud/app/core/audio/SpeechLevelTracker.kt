package com.bedrud.app.core.audio

/**
 * Decides whether a capture level is someone talking, judged against the microphone's own noise
 * floor rather than against a fixed bar.
 *
 * A fixed level cannot hold across hardware. Microphone gain, the maker's own gain control, how
 * loudly and from how far someone speaks, and the room's noise all move where speech lands on the
 * capture scale; one phone in one room needed four different values while the old bar was being
 * tuned. Both ways of missing are silent: a quiet microphone never reached the bar, so nothing
 * ever fired, and a hot one sat above it, so everything did and the warning was ignored.
 *
 * So the floor is measured instead: the lowest level among the last [WindowSamples] samples, which
 * is the room between words. It drops the moment the room goes quieter, and rises only once a
 * louder noise has filled the whole window. Speech is a level [MarginAboveFloor] above it. Nothing
 * is judged until a state has filled its window once, so joining mid-sentence does not set the
 * floor on the speaker's own voice.
 *
 * Muted and live capture keep separate floors. A muted capture is not gain-ridden for
 * transmission, so on the same device in the same room its floor sits far lower. Carrying one
 * floor across an unmute would read the live room's noise as speech until the window caught up,
 * and a window that is kept per state answers at once on every switch after the first.
 */
class SpeechLevelTracker(
    private val windowSamples: Int = WindowSamples,
    private val marginAboveFloor: Float = MarginAboveFloor,
    private val floorCap: Float = FloorCap,
) {

    private val liveWindow = ArrayDeque<Float>()
    private val mutedWindow = ArrayDeque<Float>()

    /**
     * Folds one capture sample into the floor of its microphone state, and says whether it is
     * speech.
     *
     * A null [level] means capture has stalled. It is neither speech nor a lesson: a stall reads as
     * silence, and learning from it would drop the floor to nothing.
     */
    fun isSpeech(level: Float?, isMicEnabled: Boolean): Boolean {
        if (level == null) return false
        val window = if (isMicEnabled) liveWindow else mutedWindow
        window.addLast(level)
        if (window.size > windowSamples) window.removeFirst()
        if (window.size < windowSamples) return false
        val floor = minOf(window.min(), floorCap)
        return level >= floor + marginAboveFloor
    }

    fun reset() {
        liveWindow.clear()
        mutedWindow.clear()
    }

    companion object {
        /**
         * Samples the floor is the lowest of, one every [VoiceReachMonitor.SampleIntervalMillis]:
         * two seconds.
         *
         * Long enough to always span a pause for breath, so the floor is the room rather than a
         * word. Short enough that a noise starting mid-call is absorbed before
         * [VoiceReachMonitor.ReachGraceMillis] runs out: until it is, the noise reads as speech, and
         * the room would be blamed for not hearing it.
         */
        const val WindowSamples = 40

        /**
         * How far above the floor a level must be to count as speech: 9 dB, since the capture
         * scale runs linearly over 60 dB.
         *
         * Measured on device, speech peaked near 0.84, and the room between words read anything
         * from a true zero, once noise suppression had settled, to 0.25. That puts the bar between
         * 0.15 and 0.40, well under the peaks. Over a quiet room, speech peaking between 0.15 and
         * 0.3 — under the fixed bar this replaced — still clears it.
         */
        const val MarginAboveFloor = 0.15f

        /**
         * The highest the floor may go, -27 dBFS.
         *
         * Continuous loud noise would otherwise lift the floor until real speech could not clear
         * it, which quietly switches the warning off. Capped, the bar never rises past 0.70, which
         * ordinary speech still reaches. Past the cap the noise itself reads as speech; a room that
         * loud is rare, and a bar nobody can reach is the failure this class exists to end.
         */
        const val FloorCap = 0.55f
    }
}
