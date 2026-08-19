package com.bedrud.app.ui.screens.meeting

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.bedrud.app.core.livekit.ParticipantMetadata
import com.bedrud.app.core.livekit.RoomManager
import io.livekit.android.room.participant.Participant
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.VideoTrack

/**
 * Everything a participant tile draws that comes from LiveKit's own room objects.
 *
 * Those objects mutate in place: a camera track appears on the very same [Participant] instance
 * that is already on screen, and Compose has no way to see it happen. A tile handed that unchanged
 * instance is skipped, so it kept drawing the avatar until some *other* parameter happened to
 * change — which is why turning the camera on did nothing until the mic was toggled or the tile
 * was tapped. Resolving the reads here, keyed on `RoomManager.participantVersion`, turns them into
 * plain values a tile can be handed, so the tile renders what it is given and nothing else.
 */
data class ParticipantTileState(
    val identity: String,
    val name: String,
    val avatarUrl: String?,
    /** The camera to render, or null when there is nothing to show — unpublished, or muted. */
    val cameraTrack: VideoTrack?,
    val isMicOff: Boolean,
)

/**
 * Resolves one [ParticipantTileState] per participant, recomputed whenever the room reports a
 * change through [participantVersion].
 *
 * Your own mute reads from [isLocalMicEnabled] rather than from the publication: muting flips that
 * state instantly while the track's own muted flag settles a moment later, so a publication-driven
 * badge lags your own tap — and the badge everyone else already sees is the one you expect to see
 * on yourself.
 */
@Composable
fun rememberParticipantTileStates(
    participants: List<Participant>,
    participantVersion: Int,
    localIdentity: String?,
    isLocalMicEnabled: Boolean,
): List<ParticipantTileState> =
    remember(participants, participantVersion, localIdentity, isLocalMicEnabled) {
        participants.map { participant ->
            participant.toTileState(localIdentity, isLocalMicEnabled)
        }
    }

private fun Participant.toTileState(
    localIdentity: String?,
    isLocalMicEnabled: Boolean,
): ParticipantTileState {
    val identity = identity?.value ?: RoomManager.UNKNOWN_PARTICIPANT_NAME
    val cameraPublication = getTrackPublication(Track.Source.CAMERA)
    val micPublication = getTrackPublication(Track.Source.MICROPHONE)
    return ParticipantTileState(
        identity = identity,
        name = name?.ifBlank { identity } ?: identity,
        avatarUrl = ParticipantMetadata.avatarUrl(metadata),
        cameraTrack = (cameraPublication?.track as? VideoTrack)
            ?.takeIf { !cameraPublication.muted },
        isMicOff = if (identity == localIdentity) {
            !isLocalMicEnabled
        } else {
            micPublication == null || micPublication.muted
        },
    )
}
