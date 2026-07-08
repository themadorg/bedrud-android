package com.bedrud.app.ui.screens.meeting

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ScreenShare
import androidx.compose.material.icons.automirrored.filled.StopScreenShare
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bedrud.app.R
import com.bedrud.app.core.meeting.stage.StageWire
import io.livekit.android.compose.ui.ScaleType
import io.livekit.android.compose.ui.VideoTrackView
import io.livekit.android.room.Room

@Composable
fun MeetingScreenShareStage(
    stage: StageWire.MeetingStage,
    room: Room,
    participantVersion: Int,
    isOwner: Boolean,
    onStop: () -> Unit,
    onMaximize: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ownerName = stage.ownerName.ifBlank { stage.ownerIdentity }
    val trackRef = resolveScreenShareTrack(room, stage.ownerIdentity)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            ScreenShareStageHeader(
                ownerName = ownerName,
                isOwner = isOwner,
                onStop = onStop,
                onToggleFullscreen = onMaximize,
                isFullscreen = false,
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

            ScreenShareVideoPane(
                room = room,
                ownerName = ownerName,
                trackRef = trackRef,
                participantVersion = participantVersion,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
            )
        }
    }
}

@Composable
fun MeetingScreenShareFullscreen(
    stage: StageWire.MeetingStage,
    room: Room,
    participantVersion: Int,
    isOwner: Boolean,
    onMinimize: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ownerName = stage.ownerName.ifBlank { stage.ownerIdentity }
    val trackRef = resolveScreenShareTrack(room, stage.ownerIdentity)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        ScreenShareVideoPane(
            room = room,
            ownerName = ownerName,
            trackRef = trackRef,
            participantVersion = participantVersion,
            modifier = Modifier.fillMaxSize(),
        )

        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding(),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        ) {
            ScreenShareStageHeader(
                ownerName = ownerName,
                isOwner = isOwner,
                onStop = onStop,
                onToggleFullscreen = onMinimize,
                isFullscreen = true,
            )
        }
    }
}

@Composable
private fun ScreenShareStageHeader(
    ownerName: String,
    isOwner: Boolean,
    onStop: () -> Unit,
    onToggleFullscreen: () -> Unit,
    isFullscreen: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ScreenShare,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(start = 8.dp)
                .size(18.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.meeting_stage_screenShareTitle),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.meeting_stage_screenSharePresenting, ownerName),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onToggleFullscreen) {
            Icon(
                imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                contentDescription = stringResource(
                    if (isFullscreen) {
                        R.string.meeting_contentDescription_minimizeScreenShare
                    } else {
                        R.string.meeting_contentDescription_maximizeScreenShare
                    },
                ),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isOwner) {
            IconButton(onClick = onStop) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(
                        R.string.meeting_contentDescription_stopScreenShare,
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ScreenShareVideoPane(
    room: Room,
    ownerName: String,
    trackRef: ScreenShareTrackRef?,
    participantVersion: Int,
    modifier: Modifier = Modifier,
) {
    @Suppress("UNUSED_VARIABLE")
    val trackVersion = participantVersion

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(0.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (trackRef != null && trackRef.isRenderable) {
            VideoTrackView(
                trackReference = trackRef.trackReference,
                modifier = Modifier.fillMaxSize(),
                room = room,
                mirror = false,
                scaleType = ScaleType.FitInside,
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.StopScreenShare,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp),
                )
                Text(
                    text = stringResource(R.string.meeting_stage_screenShareWaiting, ownerName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, start = 16.dp, end = 16.dp),
                )
            }
        }
    }
}