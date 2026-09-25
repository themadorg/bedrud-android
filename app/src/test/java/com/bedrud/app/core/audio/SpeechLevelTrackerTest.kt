package com.bedrud.app.core.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechLevelTrackerTest {

    private val window = SpeechLevelTracker.WindowSamples
    private val margin = SpeechLevelTracker.MarginAboveFloor
    private val cap = SpeechLevelTracker.FloorCap

    // A room quiet enough that anything said in it clears the margin.
    private val quietRoom = 0.05f
    private val speech = 0.8f

    /** Feeds [times] samples of one level, the way a steady room reaches the tracker. */
    private fun SpeechLevelTracker.hear(
        level: Float?,
        times: Int = window,
        isMicEnabled: Boolean = true,
    ) = repeat(times) { isSpeech(level, isMicEnabled) }

    @Test
    fun `should not call anything speech before a full window has been heard`() {
        val tracker = SpeechLevelTracker()
        tracker.hear(quietRoom, times = window - 2)

        assertFalse(tracker.isSpeech(speech, isMicEnabled = true))
    }

    @Test
    fun `should judge speech once a full window has been heard`() {
        val tracker = SpeechLevelTracker()
        tracker.hear(quietRoom, times = window - 1)

        assertTrue(tracker.isSpeech(speech, isMicEnabled = true))
    }

    @Test
    fun `should hear a quiet microphone that never reaches the old fixed level`() {
        val tracker = SpeechLevelTracker()
        tracker.hear(0.02f)

        // Under the 0.3 bar this replaced, which is why a quiet microphone never raised anything.
        assertTrue(tracker.isSpeech(0.28f, isMicEnabled = true))
    }

    @Test
    fun `should not mistake a hot microphone's floor for speech`() {
        val tracker = SpeechLevelTracker()
        tracker.hear(0.45f)

        assertFalse(tracker.isSpeech(0.45f, isMicEnabled = true))
        assertFalse(tracker.isSpeech(0.5f, isMicEnabled = true))
        assertTrue(tracker.isSpeech(0.9f, isMicEnabled = true))
    }

    @Test
    fun `should count a level exactly the margin above the floor as speech`() {
        val tracker = SpeechLevelTracker()
        val floor = 0.25f
        tracker.hear(floor)

        assertTrue(tracker.isSpeech(floor + margin, isMicEnabled = true))
    }

    @Test
    fun `should not count a level just under the margin above the floor as speech`() {
        val tracker = SpeechLevelTracker()
        val floor = 0.25f
        tracker.hear(floor)

        assertFalse(tracker.isSpeech(floor + margin - 0.01f, isMicEnabled = true))
    }

    @Test
    fun `should hear speech over a steady fan`() {
        val tracker = SpeechLevelTracker()
        val fan = 0.4f
        tracker.hear(fan)

        assertFalse(tracker.isSpeech(fan, isMicEnabled = true))
        assertTrue(tracker.isSpeech(fan + margin + 0.05f, isMicEnabled = true))
    }

    @Test
    fun `should stop taking a fan that starts mid-call for speech within one window`() {
        val tracker = SpeechLevelTracker()
        val fan = 0.4f
        tracker.hear(quietRoom)

        // Until the quiet samples have left the window, the fan still reads as someone talking.
        assertTrue(tracker.isSpeech(fan, isMicEnabled = true))
        tracker.hear(fan, times = window - 1)

        assertFalse(tracker.isSpeech(fan, isMicEnabled = true))
    }

    @Test
    fun `should forget a new noise before the room could be blamed for not hearing it`() {
        // A fan starting mid-call reads as speech for at most one window. The room is only
        // reported as not hearing you after the reach grace, so the window has to end first.
        val windowMillis = window * VoiceReachMonitor.SampleIntervalMillis

        assertTrue(windowMillis < VoiceReachMonitor.ReachGraceMillis)
    }

    @Test
    fun `should cap the floor so constant loud noise cannot push speech out of reach`() {
        val tracker = SpeechLevelTracker()
        tracker.hear(0.9f)

        assertTrue(tracker.isSpeech(cap + margin, isMicEnabled = true))
    }

    @Test
    fun `should find the floor in the gaps when joining mid-sentence`() {
        val tracker = SpeechLevelTracker()
        val gap = 0.1f
        // The first samples are all voice, then words with gaps between them.
        tracker.hear(speech, times = window / 4)
        repeat(window) { index ->
            tracker.isSpeech(if (index % 3 == 0) gap else speech, isMicEnabled = true)
        }

        assertTrue(tracker.isSpeech(speech, isMicEnabled = true))
        assertFalse(tracker.isSpeech(gap + margin / 2, isMicEnabled = true))
    }

    @Test
    fun `should neither count nor learn from a stalled capture`() {
        val tracker = SpeechLevelTracker()
        val floor = 0.25f
        tracker.hear(floor)

        assertFalse(tracker.isSpeech(null, isMicEnabled = true))
        tracker.hear(null)

        // Had the stall taught the floor zero, this would clear the margin.
        assertFalse(tracker.isSpeech(floor + margin / 2, isMicEnabled = true))
    }

    @Test
    fun `should warm up a microphone state the first time it is used`() {
        val tracker = SpeechLevelTracker()
        tracker.hear(0.25f, isMicEnabled = true)

        assertFalse(tracker.isSpeech(speech, isMicEnabled = false))
    }

    @Test
    fun `should keep a separate floor for each microphone state`() {
        val tracker = SpeechLevelTracker()
        val liveFloor = 0.25f
        tracker.hear(liveFloor, isMicEnabled = true)
        tracker.hear(0f, isMicEnabled = false)

        // Muted capture is not gain-ridden, so its floor sits far below the live one.
        assertTrue(tracker.isSpeech(margin, isMicEnabled = false))
        assertFalse(tracker.isSpeech(liveFloor + margin / 2, isMicEnabled = true))
    }

    @Test
    fun `should judge at once on returning to a microphone state it has heard`() {
        val tracker = SpeechLevelTracker()
        tracker.hear(0.25f, isMicEnabled = true)
        tracker.hear(0f, isMicEnabled = false)

        assertTrue(tracker.isSpeech(speech, isMicEnabled = true))
    }

    @Test
    fun `should warm up again after a reset`() {
        val tracker = SpeechLevelTracker()
        tracker.hear(quietRoom)

        tracker.reset()

        assertFalse(tracker.isSpeech(speech, isMicEnabled = true))
    }
}
