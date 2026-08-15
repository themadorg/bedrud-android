package com.bedrud.app.core.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceReachMonitorTest {

    private val loud = VoiceReachMonitor.TalkingLevel + 0.1f
    private val quiet = VoiceReachMonitor.TalkingLevel - 0.1f
    private val causeGrace = VoiceReachMonitor.CauseGraceMillis
    private val reachGrace = VoiceReachMonitor.ReachGraceMillis

    private fun VoiceReachMonitor.at(
        nowMillis: Long,
        micLevel: Float = loud,
        isMicEnabled: Boolean = true,
        isPushToTalk: Boolean = false,
        isGateOpen: Boolean = true,
        roomHearsMe: Boolean = false,
        roomHasOthers: Boolean = true,
    ) = sample(nowMillis, micLevel, isMicEnabled, isPushToTalk, isGateOpen, roomHearsMe, roomHasOthers)

    @Test
    fun `silence never raises anything`() {
        val monitor = VoiceReachMonitor()

        assertEquals(MeetingVoiceAlert.None, monitor.at(0, micLevel = quiet))
        assertEquals(MeetingVoiceAlert.None, monitor.at(10_000, micLevel = quiet))
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
        monitor.at(causeGrace / 2, micLevel = quiet, isMicEnabled = false)

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
        monitor.at(reachGrace - 1, micLevel = quiet)

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
            monitor.at(afterHold, micLevel = quiet, isMicEnabled = false),
        )
    }

    @Test
    fun `a gap between words does not drop the alert`() {
        val monitor = VoiceReachMonitor()
        monitor.at(0, isMicEnabled = false)
        assertEquals(MeetingVoiceAlert.Muted, monitor.at(causeGrace, isMicEnabled = false))

        assertEquals(
            MeetingVoiceAlert.Muted,
            monitor.at(causeGrace + 200, micLevel = quiet, isMicEnabled = false),
        )
    }

    @Test
    fun `nothing is reported while you are quiet from the start`() {
        val monitor = VoiceReachMonitor()

        assertEquals(MeetingVoiceAlert.None, monitor.at(0, micLevel = quiet, isMicEnabled = false))
        assertEquals(
            MeetingVoiceAlert.None,
            monitor.at(10 * reachGrace, micLevel = quiet, isMicEnabled = false),
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
