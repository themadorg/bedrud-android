package com.bedrud.app.ui.screens.meeting

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.livekit.android.compose.ui.ScaleType
import io.livekit.android.compose.ui.VideoTrackView
import io.livekit.android.room.Room

/**
 * What the picture-in-picture window shows of the participant [state] resolves: a screen share
 * being watched as it is, and otherwise the very tile the call's grid draws for them. In a voice
 * call that is what makes the window worth having: it used to be one large letter, and the tile
 * carries their avatar, their name, their mute and deafen badges, and the ring that shows them
 * speaking, while still showing their camera when it is on.
 */
@Composable
internal fun MeetingPipView(
    state: ParticipantTileState?,
    room: Room?,
    showsWatchedStream: Boolean,
    isLocalParticipant: Boolean = false,
    speakingLevel: Float = 0f,
    isLocalDeafened: Boolean = false,
    deafenedIdentities: Set<String> = emptySet(),
    disabledVideoIdentities: Set<String> = emptySet(),
    hideAllIncomingVideo: Boolean = false,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (state == null) return@Box
        val screenShare = state.screenShare?.takeIf { showsWatchedStream }
        if (screenShare != null && room != null) {
            VideoTrackView(
                trackReference = screenShare.trackReference,
                modifier = Modifier.fillMaxSize(),
                room = room,
                mirror = false,
                scaleType = ScaleType.FitInside,
            )
        } else {
            ParticipantTile(
                state = state,
                isLocalParticipant = isLocalParticipant,
                room = room,
                disabledVideoIdentities = disabledVideoIdentities,
                hideAllIncomingVideo = hideAllIncomingVideo,
                speakingLevel = speakingLevel,
                isLocalDeafened = isLocalDeafened,
                deafenedIdentities = deafenedIdentities,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
