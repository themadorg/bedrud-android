package com.bedrud.app.core.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceReachMonitorTest {

    private val causeGrace = VoiceReachMonitor.CauseGraceMillis
    private val reachGrace = VoiceReachMonitor.ReachGraceMillis
    private val roomSpeakerLevel = VoiceReachMonitor.RoomSpeakerLevel

    // Speech loud enough for the room to report, and speech it would stay silent about.
    private val loud = roomSpeakerLevel + 0.1f
    private val belowRoom = roomSpeakerLevel - 0.2f

    private fun VoiceReachMonitor.at(
        nowMillis: Long,
        micLevel: Float? = loud,
        isSpeech: Boolean = true,
        isMicEnabled: Boolean = true,
        isPushToTalk: Boolean = false,
        isGateOpen: Boolean = true,
        roomHearsMe: Boolean = false,
        roomHasOthers: Boolean = true,
    ) = sample(
        nowMillis,
        micLevel,
        isSpeech,
        isMicEnabled,
        isPushToTalk,
        isGateOpen,
        roomHearsMe,
        roomHasOthers,
    )

    @Test
    fun `silence never raises anything`() {
        val monitor = VoiceReachMonitor()

        assertEquals(MeetingVoiceAlert.None, monitor.at(0, isSpeech = false))
        assertEquals(MeetingVoiceAlert.None, monitor.at(10_000, isSpeech = false))
    }

    @Test
    fun `a single loud frame is a cough, not a sentence`() {
        val monitor = VoiceReachMonitor()

        assertEquals(MeetingVoiceAlert.None, monitor.at(0, isMicEnabled = false))
    }

    @Test
    fun `a gap between words does not restart the wait`() {
        val monitor = VoiceReachMonitor()
        monitor.at(0, isMicEnabled = false)
        // Quiet for less than the hold, then loud again: the run continues rather than resetting,
        // so a normal sentence still reaches the grace period.
        monitor.at(causeGrace / 2, isSpeech = false, isMicEnabled = false)

        assertEquals(MeetingVoiceAlert.Muted, monitor.at(causeGrace, isMicEnabled = false))
    }

    @Test
    fun `talking while muted is called out`() {
        val monitor = VoiceReachMonitor()
        monitor.at(0, isMicEnabled = false)

        assertEquals(MeetingVoiceAlert.Muted, monitor.at(causeGrace, isMicEnabled = false))
    }

    @Test
    fun `push-to-talk gets its own instruction rather than a mute warning`() {
        val monitor = VoiceReachMonitor()
        monitor.at(0, isMicEnabled = false, isPushToTalk = true)

        assertEquals(
            MeetingVoiceAlert.PushToTalkIdle,
            monitor.at(causeGrace, isMicEnabled = false, isPushToTalk = true),
        )
    }

    @Test
    fun `a shut voice gate is named as the cause`() {
        val monitor = VoiceReachMonitor()
        monitor.at(0, isGateOpen = false)

        assertEquals(MeetingVoiceAlert.GateClosed, monitor.at(causeGrace, isGateOpen = false))
    }

    @Test
    fun `a live mic the room never reports is a failure`() {
        val monitor = VoiceReachMonitor()
        monitor.at(0)

        assertEquals(MeetingVoiceAlert.None, monitor.at(reachGrace - 1))
        assertEquals(MeetingVoiceAlert.NotReachingRoom, monitor.at(reachGrace))
    }

    @Test
    fun `should not blame the room for speech too quiet for it to report`() {
        val monitor = VoiceReachMonitor()
        monitor.at(0, micLevel = belowRoom)

        assertEquals(MeetingVoiceAlert.None, monitor.at(reachGrace, micLevel = belowRoom))
        assertEquals(MeetingVoiceAlert.None, monitor.at(10 * reachGrace, micLevel = belowRoom))
    }

    @Test
    fun `should blame the room for speech exactly at the level it reports`() {
        val monitor = VoiceReachMonitor()
        monitor.at(0, micLevel = roomSpeakerLevel)

        assertEquals(
            MeetingVoiceAlert.NotReachingRoom,
            monitor.at(reachGrace, micLevel = roomSpeakerLevel),
        )
    }

    @Test
    fun `should not blame the room for speech just under the level it reports`() {
        val monitor = VoiceReachMonitor()
        val justUnder = roomSpeakerLevel - 0.01f
        monitor.at(0, micLevel = justUnder)

        assertEquals(MeetingVoiceAlert.None, monitor.at(reachGrace, micLevel = justUnder))
    }

    @Test
    fun `should restart the wait once speech drops below what the room reports`() {
        val monitor = VoiceReachMonitor()
        monitor.at(0)
        // Still talking, but too quietly for the room to report, for longer than the hold.
        monitor.at(reachGrace / 2, micLevel = belowRoom)

        assertEquals(MeetingVoiceAlert.None, monitor.at(reachGrace, micLevel = belowRoom))
    }

    @Test
    fun `should still report a local cause for speech too quiet for the room`() {
        val monitor = VoiceReachMonitor()
        monitor.at(0, micLevel = belowRoom, isMicEnabled = false)

        assertEquals(
            MeetingVoiceAlert.Muted,
            monitor.at(causeGrace, micLevel = belowRoom, isMicEnabled = false),
        )
    }

    @Test
    fun `should not blame the room for a loud level that is not speech`() {
        val monitor = VoiceReachMonitor()
        // A hot microphone's own floor can sit above the room's level without being speech.
        monitor.at(0, isSpeech = false)

        assertEquals(MeetingVoiceAlert.None, monitor.at(reachGrace, isSpeech = false))
    }

    @Test
    fun `the room hearing you keeps the failure away`() {
        val monitor = VoiceReachMonitor()
        monitor.at(0, roomHearsMe = true)

        assertEquals(MeetingVoiceAlert.None, monitor.at(reachGrace, roomHearsMe = true))
        // Still covered by the last confirmation, so a burst-shaped gap is not a failure.
        assertEquals(MeetingVoiceAlert.None, monitor.at(reachGrace + 1))
    }

    @Test
    fun `going quiet restarts the wait`() {
        val monitor = VoiceReachMonitor()
        monitor.at(0)
        monitor.at(reachGrace - 1, isSpeech = false)

        assertEquals(MeetingVoiceAlert.None, monitor.at(reachGrace))
        assertEquals(MeetingVoiceAlert.NotReachingRoom, monitor.at(2 * reachGrace))
    }

    @Test
    fun `alone in the room, silence from the server proves nothing`() {
        val monitor = VoiceReachMonitor()
        monitor.at(0, roomHasOthers = false)

        assertEquals(MeetingVoiceAlert.None, monitor.at(reachGrace, roomHasOthers = false))
        assertEquals(MeetingVoiceAlert.None, monitor.at(10 * reachGrace, roomHasOthers = false))
    }

    @Test
    fun `alone in the room, local causes are still reported`() {
        val monitor = VoiceReachMonitor()
        monitor.at(0, isMicEnabled = false, roomHasOthers = false)

        assertEquals(
            MeetingVoiceAlert.Muted,
            monitor.at(causeGrace, isMicEnabled = false, roomHasOthers = false),
        )
    }

    @Test
    fun `the alert clears once you stop talking`() {
        val monitor = VoiceReachMonitor()
        monitor.at(0, isMicEnabled = false)
        assertEquals(MeetingVoiceAlert.Muted, monitor.at(causeGrace, isMicEnabled = false))

        val afterHold = causeGrace + VoiceReachMonitor.QuietHoldMillis
        assertEquals(
            MeetingVoiceAlert.None,
            monitor.at(afterHold, isSpeech = false, isMicEnabled = false),
        )
    }

    @Test
    fun `a gap between words does not drop the alert`() {
        val monitor = VoiceReachMonitor()
        monitor.at(0, isMicEnabled = false)
        assertEquals(MeetingVoiceAlert.Muted, monitor.at(causeGrace, isMicEnabled = false))

        assertEquals(
            MeetingVoiceAlert.Muted,
            monitor.at(causeGrace + 200, isSpeech = false, isMicEnabled = false),
        )
    }

    @Test
    fun `nothing is reported while you are quiet from the start`() {
        val monitor = VoiceReachMonitor()

        assertEquals(MeetingVoiceAlert.None, monitor.at(0, isSpeech = false, isMicEnabled = false))
        assertEquals(
            MeetingVoiceAlert.None,
            monitor.at(10 * reachGrace, isSpeech = false, isMicEnabled = false),
        )
    }

    @Test
    fun `a blip in the cause mid-sentence does not flash`() {
        val monitor = VoiceReachMonitor()
        // Talking happily, unmuted, for well past every grace period.
        repeat(6) { i -> monitor.at(i * 200L) }

        // One sample catches the mic mid-toggle. The cause is brand new even though the talking
        // is not, so nothing is raised.
        assertEquals(MeetingVoiceAlert.None, monitor.at(1_200, isMicEnabled = false))
        assertEquals(MeetingVoiceAlert.None, monitor.at(1_400, isMicEnabled = false))
    }

    @Test
    fun `a cause that persists while talking is still reported`() {
        val monitor = VoiceReachMonitor()
        repeat(6) { i -> monitor.at(i * 200L) }

        monitor.at(1_200, isMicEnabled = false)
        assertEquals(
            MeetingVoiceAlert.Muted,
            monitor.at(1_200 + causeGrace, isMicEnabled = false),
        )
    }

    @Test
    fun `reset clears the confirmation from a previous call`() {
        val monitor = VoiceReachMonitor()
        monitor.at(0, roomHearsMe = true)

        monitor.reset()

        monitor.at(0)
        assertEquals(MeetingVoiceAlert.NotReachingRoom, monitor.at(reachGrace))
    }
}
