package com.bedrud.app.ui.screens.meeting

import io.livekit.android.compose.types.TrackReference
import io.livekit.android.room.Room
import io.livekit.android.room.participant.Participant
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.TrackPublication
import io.livekit.android.room.track.VideoTrack

data class ScreenShareTrackRef(
    val participant: Participant,
    val publication: TrackPublication,
) {
    val track: VideoTrack?
        get() = publication.track as? VideoTrack

    val trackReference: TrackReference
        get() = TrackReference(participant, publication, Track.Source.SCREEN_SHARE)

    val isRenderable: Boolean
        get() = !publication.muted
}

fun resolveScreenShareTrack(room: Room, ownerIdentity: String): ScreenShareTrackRef? {
    val participants = buildList {
        add(room.localParticipant)
        addAll(room.remoteParticipants.values)
    }

    val owner = participants.firstOrNull { it.identity?.value == ownerIdentity }
    owner?.let { participant ->
        resolveScreenSharePublication(participant)?.let { publication ->
            return ScreenShareTrackRef(participant, publication)
        }
    }

    // Stage owner identity can lag behind track publish — show any active screen share.
    return participants.firstNotNullOfOrNull { participant ->
        val publication = resolveScreenSharePublication(participant) ?: return@firstNotNullOfOrNull null
        if (publication.muted) return@firstNotNullOfOrNull null
        ScreenShareTrackRef(participant, publication)
    }
}

fun resolveParticipantScreenShare(participant: Participant): ScreenShareTrackRef? {
    val publication = resolveScreenSharePublication(participant) ?: return null
    if (publication.muted) return null
    return ScreenShareTrackRef(participant, publication)
}

private fun resolveScreenSharePublication(participant: Participant): TrackPublication? {
    participant.getTrackPublication(Track.Source.SCREEN_SHARE)?.let { return it }
    return participant.trackPublications.values.firstOrNull { publication ->
        publication.source == Track.Source.SCREEN_SHARE
    }
}