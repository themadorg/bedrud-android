package com.bedrud.app.ui.screens.meeting

import io.livekit.android.room.Room
import io.livekit.android.room.participant.LocalParticipant
import io.livekit.android.room.participant.Participant
import io.livekit.android.room.participant.RemoteParticipant
import io.livekit.android.room.track.LocalTrackPublication
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.TrackPublication
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * A screen share as LiveKit reports it: [muted] while its sender has paused it. Your own comes back
 * as a [LocalTrackPublication], so a test asks for that kind of [T] for it.
 */
private inline fun <reified T : TrackPublication> shareOf(muted: Boolean = false) = mockk<T> {
    every { this@mockk.muted } returns muted
    every { source } returns Track.Source.SCREEN_SHARE
}

private fun share(muted: Boolean = false) = shareOf<TrackPublication>(muted)

/**
 * Stubs a participant called [identity] sharing [screenShare], if anything. LiveKit can know a share
 * before it is filed under its source, so [listedOnlyByTrack] leaves it out of that lookup.
 */
private fun stubParticipant(
    participant: Participant,
    identity: String,
    screenShare: TrackPublication?,
    listedOnlyByTrack: Boolean,
) {
    every { participant.identity } returns Participant.Identity(identity)
    every { participant.getTrackPublication(Track.Source.SCREEN_SHARE) } returns
        screenShare.takeUnless { listedOnlyByTrack }
    every { participant.trackPublications } returns
        if (screenShare == null) emptyMap() else mapOf("share-track" to screenShare)
}

private fun local(identity: String, screenShare: TrackPublication? = null) =
    mockk<LocalParticipant>().also { stubParticipant(it, identity, screenShare, listedOnlyByTrack = false) }

private fun remote(identity: String, screenShare: TrackPublication? = null, listedOnlyByTrack: Boolean = false) =
    mockk<RemoteParticipant>().also { stubParticipant(it, identity, screenShare, listedOnlyByTrack) }

private fun room(local: LocalParticipant, vararg remotes: RemoteParticipant) = mockk<Room> {
    every { localParticipant } returns local
    every { remoteParticipants } returns remotes.associateBy { it.identity!! }
}

/**
 * Which screen share the call puts on stage. The stage names its owner, but that name can arrive
 * after the share itself, so any running share stands in until it does.
 */
class ScreenShareTrackResolverTest {

    private val me = local("me")

    @Test
    fun `should put the stage owner's share on stage`() {
        val ownerShare = share()
        val owner = remote("owner", ownerShare)
        val other = remote("other", share())

        val resolved = resolveScreenShareTrack(room(me, other, owner), ownerIdentity = "owner")

        assertSame(owner, resolved?.participant)
        assertSame(ownerShare, resolved?.publication)
    }

    /** A paused share keeps the stage, drawn as paused, rather than handing it to someone else. */
    @Test
    fun `should keep the owner's paused share on stage, as not renderable`() {
        val owner = remote("owner", share(muted = true))

        val resolved = resolveScreenShareTrack(room(me, owner, remote("other", share())), ownerIdentity = "owner")

        assertSame(owner, resolved?.participant)
        assertFalse(resolved!!.isRenderable)
    }

    @Test
    fun `should show any running share while the owner's name lags behind it`() {
        val sharer = remote("sharer", share())

        val resolved = resolveScreenShareTrack(room(me, remote("owner"), sharer), ownerIdentity = "owner")

        assertSame(sharer, resolved?.participant)
    }

    @Test
    fun `should pass over paused shares when standing in for the owner`() {
        val resolved = resolveScreenShareTrack(room(me, remote("paused", share(muted = true))), ownerIdentity = "owner")

        assertNull(resolved)
    }

    @Test
    fun `should find your own share`() {
        val myShare = shareOf<LocalTrackPublication>()
        val sharingMe = local("me", myShare)

        val resolved = resolveScreenShareTrack(room(sharingMe), ownerIdentity = "me")

        assertSame(myShare, resolved?.publication)
    }

    @Test
    fun `should find nothing when nobody shares`() {
        assertNull(resolveScreenShareTrack(room(me, remote("other")), ownerIdentity = "other"))
    }

    @Test
    fun `should find a participant's share that is listed only by its track`() {
        val listedShare = share()
        val sharer = remote("sharer", listedShare, listedOnlyByTrack = true)

        assertSame(listedShare, resolveParticipantScreenShare(sharer)?.publication)
    }

    @Test
    fun `should give a participant no share while theirs is paused`() {
        assertNull(resolveParticipantScreenShare(remote("sharer", share(muted = true))))
    }
}
