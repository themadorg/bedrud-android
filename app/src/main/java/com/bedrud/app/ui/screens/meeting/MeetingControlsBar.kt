package com.bedrud.app.ui.screens.meeting

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ScreenShare
import androidx.compose.material.icons.automirrored.filled.StopScreenShare
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bedrud.app.R

@Composable
fun MeetingControlsBar(
    isMicEnabled: Boolean,
    isCameraEnabled: Boolean,
    micHasError: Boolean = false,
    cameraHasError: Boolean = false,
    isScreenShareEnabled: Boolean,
    showChat: Boolean,
    showParticipants: Boolean,
    unreadCount: Int,
    onToggleMic: () -> Unit,
    onToggleCamera: () -> Unit,
    onSwitchCamera: () -> Unit,
    onToggleScreenShare: () -> Unit,
    onToggleChat: () -> Unit,
    onToggleParticipants: () -> Unit,
    onOpenAudioSettings: () -> Unit,
    onEndCall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = meetingChromeColors()
    var showMoreMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.navigationBarsPadding(),
        shape = RoundedCornerShape(28.dp),
        color = colors.bar,
        shadowElevation = 4.dp,
        tonalElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.divider),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MeetMediaButton(
                colors = colors,
                enabled = isCameraEnabled,
                hasError = cameraHasError,
                onClick = onToggleCamera,
                enabledIcon = Icons.Default.Videocam,
                disabledIcon = Icons.Default.VideocamOff,
                contentDescription = stringResource(R.string.meeting_contentDescription_toggleCamera),
            )

            MeetCircleButton(
                colors = colors,
                onClick = onToggleScreenShare,
                icon = if (isScreenShareEnabled) Icons.AutoMirrored.Filled.StopScreenShare
                else Icons.AutoMirrored.Filled.ScreenShare,
                contentDescription = stringResource(R.string.meeting_contentDescription_toggleScreenShare),
                containerColor = if (isScreenShareEnabled) colors.buttonActive else colors.button,
            )

            MeetMediaButton(
                colors = colors,
                enabled = isMicEnabled,
                hasError = micHasError,
                onClick = onToggleMic,
                enabledIcon = Icons.Default.Mic,
                disabledIcon = Icons.Default.MicOff,
                contentDescription = stringResource(R.string.meeting_contentDescription_toggleMic),
            )

            MeetCircleButton(
                colors = colors,
                onClick = onToggleChat,
                icon = Icons.AutoMirrored.Filled.Chat,
                contentDescription = stringResource(R.string.meeting_contentDescription_toggleChat),
                containerColor = if (showChat) colors.buttonActive else colors.button,
                badge = if (unreadCount > 0) {
                    if (unreadCount > 9) "9+" else unreadCount.toString()
                } else {
                    null
                },
            )

            MeetCircleButton(
                colors = colors,
                onClick = { showMoreMenu = true },
                icon = Icons.Default.MoreHoriz,
                contentDescription = stringResource(R.string.meeting_contentDescription_moreOptions),
                containerColor = if (showMoreMenu || showParticipants) {
                    colors.buttonActive
                } else {
                    colors.button
                },
            )

            if (showMoreMenu) {
                MeetingMoreOptionsSheet(
                    isCameraEnabled = isCameraEnabled,
                    unreadCount = unreadCount,
                    onDismiss = { showMoreMenu = false },
                    onSwitchCamera = onSwitchCamera,
                    onToggleChat = onToggleChat,
                    onToggleParticipants = onToggleParticipants,
                    onOpenAudioSettings = {
                        showMoreMenu = false
                        onOpenAudioSettings()
                    },
                )
            }

            Box(
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .width(1.dp)
                    .height(32.dp)
                    .background(colors.divider),
            )

            MeetEndCallButton(colors = colors, onClick = onEndCall)
        }
    }
}

@Composable
private fun MeetMediaButton(
    colors: MeetingChromeColors,
    enabled: Boolean,
    hasError: Boolean = false,
    onClick: () -> Unit,
    enabledIcon: ImageVector,
    disabledIcon: ImageVector,
    contentDescription: String,
) {
    Box(modifier = Modifier.size(width = 52.dp, height = 44.dp)) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(12.dp),
            color = if (enabled) colors.button else colors.buttonMediaOff,
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (enabled) enabledIcon else disabledIcon,
                    contentDescription = contentDescription,
                    tint = if (enabled) colors.onButton else colors.onButtonMediaOff,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        if (hasError) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
                    .size(16.dp)
                    .background(colors.warning, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "!",
                    color = colors.onWarning,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun MeetCircleButton(
    colors: MeetingChromeColors,
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    containerColor: Color,
    badge: String? = null,
) {
    val button = @Composable {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = containerColor,
            modifier = Modifier.size(40.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = colors.onButton,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }

    if (badge != null) {
        BadgedBox(
            badge = {
                Badge { Text(badge) }
            },
        ) {
            button()
        }
    } else {
        button()
    }
}

@Composable
private fun MeetEndCallButton(
    colors: MeetingChromeColors,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = colors.endCall,
        modifier = Modifier.size(52.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.CallEnd,
                contentDescription = stringResource(R.string.meeting_contentDescription_leaveCall),
                tint = colors.onEndCall,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}