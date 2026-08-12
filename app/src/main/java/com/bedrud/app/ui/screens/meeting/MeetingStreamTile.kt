package com.bedrud.app.ui.screens.meeting

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ScreenShare
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import com.bedrud.app.R
import com.bedrud.app.core.livekit.RoomManager
import com.bedrud.app.core.meeting.VideoAspect
import com.bedrud.app.ui.theme.BedrudShapeTokens
import com.bedrud.app.ui.theme.Dimens
import io.livekit.android.compose.ui.ScaleType
import io.livekit.android.compose.ui.VideoTrackView
import io.livekit.android.room.Room
import io.livekit.android.room.participant.Participant

/** Scrim opacity behind stream tile chrome, matching the participant tiles. */
private const val StreamChipAlpha = 0.7f

/**
 * One screenshare in the streams strip above the grid. Watching is opt-in per viewer: an
 * unwatched share renders as a placeholder with a watch button, the watched one plays inline
 * (long-press for its sheet, corner action for fullscreen), and your own share shows a stop
 * control instead of offering to watch yourself.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MeetingStreamTile(
    participant: Participant,
    isLocal: Boolean,
    isWatched: Boolean,
    room: Room,
    participantVersion: Int,
    onWatch: () -> Unit,
    onExpand: () -> Unit,
    onOpenStreamSheet: () -> Unit,
    onStopShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    @Suppress("UNUSED_VARIABLE")
    val trackVersion = participantVersion

    val identity = participant.identity?.value ?: RoomManager.UNKNOWN_PARTICIPANT_NAME
    val name = participant.name?.ifBlank { identity } ?: identity

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(VideoAspect.RATIO)
            .clip(BedrudShapeTokens.videoTile)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(
                if (isWatched && !isLocal) {
                    Modifier.combinedClickable(
                        onClick = {},
                        onLongClick = onOpenStreamSheet,
                    )
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        when {
            isLocal -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Dimens.space8),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ScreenShare,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(Dimens.iconLg),
                    )
                    Text(
                        text = stringResource(R.string.meeting_stream_youArePresenting),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FilledTonalButton(onClick = onStopShare) {
                        Text(stringResource(R.string.meeting_stream_stop))
                    }
                }
            }
            isWatched -> {
                val trackRef = resolveParticipantScreenShare(participant)
                if (trackRef != null && trackRef.isRenderable) {
                    VideoTrackView(
                        trackReference = trackRef.trackReference,
                        modifier = Modifier.fillMaxSize(),
                        room = room,
                        mirror = false,
                        scaleType = ScaleType.FitInside,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.meeting_stage_screenShareWaiting, name),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Dimens.space16),
                    )
                }

                Surface(
                    onClick = onExpand,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = StreamChipAlpha),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(Dimens.space6)
                        .size(Dimens.meetingTileAction),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = stringResource(
                                R.string.meeting_contentDescription_tileFullscreen
                            ),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(Dimens.iconSm),
                        )
                    }
                }
            }
            else -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Dimens.space8),
                ) {
                    Text(
                        text = stringResource(R.string.meeting_stage_screenSharePresenting, name),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FilledTonalButton(onClick = onWatch) {
                        Text(stringResource(R.string.meeting_stream_watch))
                    }
                }
            }
        }

        // Name chip, bottom center — same chrome language as the participant tiles
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(Dimens.space8)
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = StreamChipAlpha),
                    BedrudShapeTokens.chip,
                )
                .padding(horizontal = Dimens.space8, vertical = Dimens.space4),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.space4),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ScreenShare,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(Dimens.meetingBadgeIcon),
            )
            Text(
                text = name,
                style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Content),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
