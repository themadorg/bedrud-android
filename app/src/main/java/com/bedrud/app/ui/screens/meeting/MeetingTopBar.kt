package com.bedrud.app.ui.screens.meeting

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import com.bedrud.app.R
import com.bedrud.app.core.livekit.ConnectionState
import com.bedrud.app.ui.components.DevOnly
import com.bedrud.app.ui.theme.Dimens

/**
 * The in-call top bar: invite/participants entry at the start, the room name (with recording and
 * reconnecting indicators) in the center, camera flip (only while the local camera is live) and
 * audio output at the end.
 *
 * The center block is weighted against the side slots so the room name stays visually centered
 * regardless of how many trailing actions are present.
 */
@Composable
fun MeetingTopBar(
    roomName: String,
    connectionState: ConnectionState,
    isCameraEnabled: Boolean,
    onInvite: () -> Unit,
    onSwitchCamera: () -> Unit,
    onOpenAudioOutput: () -> Unit,
    onRecordingClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = meetingChromeColors()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.space4, vertical = Dimens.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Start slot
        Row(modifier = Modifier.weight(1f)) {
            IconButton(onClick = onInvite) {
                Icon(
                    imageVector = Icons.Default.PersonAdd,
                    contentDescription = stringResource(R.string.meeting_contentDescription_invite),
                    tint = colors.onButton,
                )
            }
        }

        // Center: room name + indicators
        Row(
            modifier = Modifier.weight(2f),
            horizontalArrangement = Arrangement.spacedBy(Dimens.space6, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = roomName,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    textDirection = TextDirection.Ltr,
                ),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Recording indicator — dev-gated placeholder until the server exposes recording
            // state. TODO(#107): drive from real room recording state and drop the gate.
            DevOnly {
                RecordingDot(onClick = onRecordingClick)
            }
            if (connectionState == ConnectionState.RECONNECTING) {
                ReconnectingDot()
            }
        }

        // End slot
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isCameraEnabled) {
                IconButton(onClick = onSwitchCamera) {
                    Icon(
                        imageVector = Icons.Default.Cameraswitch,
                        contentDescription = stringResource(R.string.meeting_contentDescription_switchCamera),
                        tint = colors.onButton,
                    )
                }
            }
            IconButton(onClick = onOpenAudioOutput) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = stringResource(R.string.meeting_contentDescription_audioOutput),
                    tint = colors.onButton,
                )
            }
        }
    }
}

@Composable
private fun RecordingDot(onClick: () -> Unit) {
    val description = stringResource(R.string.meeting_contentDescription_recording)
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(Dimens.space4),
    ) {
        Box(
            modifier = Modifier
                .size(Dimens.meetingIndicatorDot)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.error)
                .semantics { contentDescription = description },
        )
    }
}

@Composable
private fun ReconnectingDot() {
    val description = stringResource(R.string.meeting_status_reconnecting)
    Box(
        modifier = Modifier
            .size(Dimens.meetingIndicatorDot)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.tertiary)
            .semantics { contentDescription = description },
    )
}
