package com.bedrud.app.ui.screens.meeting

import com.bedrud.app.core.livekit.RoomManager
import io.livekit.android.room.participant.Participant
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.TrackPublication
import io.livekit.android.room.track.VideoTrack
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

private const val LocalIdentity = "me"

/** A published track, [muted] while its sender has it off, carrying [track] when there is one. */
private fun publication(muted: Boolean = false, track: Track? = null) = mockk<TrackPublication> {
    every { this@mockk.muted } returns muted
    every { this@mockk.track } returns track
    every { dimensions } returns null
}

/** A participant as LiveKit reports one, with as much or as little published as a test needs. */
private fun participant(
    identity: String? = "other",
    name: String? = "Other",
    camera: TrackPublication? = null,
    microphone: TrackPublication? = null,
) = mockk<Participant> {
    every { this@mockk.identity } returns identity?.let { Participant.Identity(it) }
    every { this@mockk.name } returns name
    every { metadata } returns null
    every { getTrackPublication(Track.Source.CAMERA) } returns camera
    every { getTrackPublication(Track.Source.MICROPHONE) } returns microphone
    every { getTrackPublication(Track.Source.SCREEN_SHARE) } returns null
    every { trackPublications } returns emptyMap()
}

/** What a participant's tile draws, read once from LiveKit's objects into plain values. */
class ParticipantTileStateTest {

    private fun tileOf(participant: Participant, isLocalMicEnabled: Boolean = true) =
        participant.toTileState(LocalIdentity, isLocalMicEnabled)

    @Test
    fun `should name a tile by the participant's name`() {
        assertEquals("Other", tileOf(participant()).name)
    }

    @Test
    fun `should name a tile by the identity when the name is blank or missing`() {
        assertEquals("other", tileOf(participant(name = "  ")).name)
        assertEquals("other", tileOf(participant(name = null)).name)
    }

    @Test
    fun `should name a participant with no identity as unknown`() {
        assertEquals(RoomManager.UNKNOWN_PARTICIPANT_NAME, tileOf(participant(identity = null, name = null)).identity)
    }

    @Test
    fun `should show a camera that is on`() {
        val video = mockk<VideoTrack>()

        assertSame(video, tileOf(participant(camera = publication(track = video))).cameraTrack)
    }

    @Test
    fun `should show no camera while it is off`() {
        assertNull(tileOf(participant(camera = publication(muted = true, track = mockk<VideoTrack>()))).cameraTrack)
        assertNull(tileOf(participant()).cameraTrack)
    }

    @Test
    fun `should mark another participant's mic off when it is muted or not published`() {
        assertTrue(tileOf(participant(microphone = publication(muted = true))).isMicOff)
        assertTrue(tileOf(participant(microphone = null)).isMicOff)
        assertFalse(tileOf(participant(microphone = publication(muted = false))).isMicOff)
    }

    /**
     * Your own mic follows your toggle, which flips at once, rather than the publication, whose
     * muted flag settles a moment later and would make your own badge lag your tap.
     */
    @Test
    fun `should mark your own mic by your toggle rather than by the publication`() {
        val me = participant(identity = LocalIdentity, microphone = publication(muted = false))

        assertTrue(tileOf(me, isLocalMicEnabled = false).isMicOff)
        assertFalse(tileOf(participant(identity = LocalIdentity, microphone = publication(muted = true)), isLocalMicEnabled = true).isMicOff)
    }
}
