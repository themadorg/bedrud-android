package com.bedrud.app.ui.screens.meeting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ScreenShare
import androidx.compose.material.icons.automirrored.filled.StopScreenShare
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.bedrud.app.R
import com.bedrud.app.ui.components.BedrudSheetActionRow
import com.bedrud.app.ui.theme.Dimens

/**
 * The pull-up options sheet behind the controls bar's drag handle. The call controls repeat along
 * its top so pulling the bar up never takes them away, then the room-level options follow as
 * standard sheet rows.
 *
 * Noise suppression ships as a dev-only "coming soon" row. TODO(#106): wire the selector once the
 * suppression modes exist.
 */
@Composable
fun MeetingMoreOptionsSheet(
    isMicEnabled: Boolean,
    isCameraEnabled: Boolean,
    isScreenShareEnabled: Boolean,
    showChat: Boolean,
    unreadCount: Int,
    isDeafened: Boolean,
    hideAllIncomingVideo: Boolean,
    isRoomSettingsAvailable: Boolean,
    onToggleMic: () -> Unit,
    onToggleCamera: () -> Unit,
    onToggleScreenShare: () -> Unit,
    onToggleChat: () -> Unit,
    onEndCall: () -> Unit,
    onToggleDeafen: () -> Unit,
    onToggleHideAllIncomingVideo: () -> Unit,
    onOpenAudioSettings: () -> Unit,
    onOpenNoiseSuppression: () -> Unit,
    onOpenInvite: () -> Unit,
    onOpenRoomSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = meetingChromeColors()

    MeetingBottomSheet(onDismiss = onDismiss) {
        // The call controls, mirrored from the bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Dimens.space8),
            horizontalArrangement = Arrangement.spacedBy(
                Dimens.space16,
                Alignment.CenterHorizontally,
            ),
        ) {
            SheetCircleAction(
                colors = colors,
                icon = if (isCameraEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                contentDescription = stringResource(R.string.meeting_contentDescription_toggleCamera),
                containerColor = if (isCameraEnabled) colors.button else colors.buttonMediaOff,
                tint = if (isCameraEnabled) colors.onButton else colors.onButtonMediaOff,
                onClick = onToggleCamera,
            )
            SheetCircleAction(
                colors = colors,
                icon = if (isScreenShareEnabled) Icons.AutoMirrored.Filled.StopScreenShare
                else Icons.AutoMirrored.Filled.ScreenShare,
                contentDescription = stringResource(R.string.meeting_contentDescription_toggleScreenShare),
                containerColor = if (isScreenShareEnabled) colors.buttonActive else colors.button,
                onClick = onToggleScreenShare,
            )
            SheetCircleAction(
                colors = colors,
                icon = if (isMicEnabled) Icons.Default.Mic else Icons.Default.MicOff,
                contentDescription = stringResource(R.string.meeting_contentDescription_toggleMic),
                containerColor = if (isMicEnabled) colors.button else colors.buttonMediaOff,
                tint = if (isMicEnabled) colors.onButton else colors.onButtonMediaOff,
                onClick = onToggleMic,
            )
            SheetCircleAction(
                colors = colors,
                icon = Icons.AutoMirrored.Filled.Chat,
                contentDescription = stringResource(R.string.meeting_contentDescription_toggleChat),
                containerColor = if (showChat) colors.buttonActive else colors.button,
                badge = if (unreadCount > 0) {
                    if (unreadCount > 9) "9+" else unreadCount.toString()
                } else {
                    null
                },
                onClick = {
                    onToggleChat()
                    onDismiss()
                },
            )
            SheetCircleAction(
                colors = colors,
                icon = Icons.Default.CallEnd,
                contentDescription = stringResource(R.string.meeting_contentDescription_leaveCall),
                containerColor = colors.endCall,
                tint = colors.onEndCall,
                onClick = {
                    onDismiss()
                    onEndCall()
                },
            )
        }

        HorizontalDivider(color = colors.divider)

        BedrudSheetActionRow(
            icon = if (isDeafened) Icons.AutoMirrored.Filled.VolumeOff
            else Icons.AutoMirrored.Filled.VolumeUp,
            title = stringResource(R.string.meeting_sheet_deafen),
            contentColor = if (isDeafened) colors.selected else colors.onButton,
            onClick = {
                onToggleDeafen()
                onDismiss()
            },
        )
        BedrudSheetActionRow(
            icon = if (hideAllIncomingVideo) Icons.Default.Videocam else Icons.Default.VideocamOff,
            title = stringResource(
                if (hideAllIncomingVideo) R.string.meeting_sheet_showAllCameras
                else R.string.meeting_sheet_disableAllCameras
            ),
            supportingText = stringResource(R.string.meeting_sheet_disableAllCamerasDescription),
            contentColor = if (hideAllIncomingVideo) colors.selected else colors.onButton,
            supportingColor = colors.onButtonVariant,
            onClick = {
                onToggleHideAllIncomingVideo()
                onDismiss()
            },
        )
        BedrudSheetActionRow(
            icon = Icons.Default.Headset,
            title = stringResource(R.string.meeting_sheet_audioSettings),
            contentColor = colors.onButton,
            onClick = {
                onDismiss()
                onOpenAudioSettings()
            },
        )
        BedrudSheetActionRow(
            icon = Icons.Default.GraphicEq,
            title = stringResource(R.string.meeting_sheet_noiseSuppression),
            contentColor = colors.onButton,
            onClick = {
                onDismiss()
                onOpenNoiseSuppression()
            },
        )
        BedrudSheetActionRow(
            icon = Icons.Default.PersonAdd,
            title = stringResource(R.string.meeting_sheet_inviteFriend),
            contentColor = colors.onButton,
            onClick = {
                onDismiss()
                onOpenInvite()
            },
        )
        if (isRoomSettingsAvailable) {
            BedrudSheetActionRow(
                icon = Icons.Default.Settings,
                title = stringResource(R.string.meeting_sheet_roomSettings),
                contentColor = colors.onButton,
                onClick = {
                    onDismiss()
                    onOpenRoomSettings()
                },
            )
        }
    }
}

@Composable
private fun SheetCircleAction(
    colors: MeetingChromeColors,
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    containerColor: Color = colors.button,
    tint: Color = colors.onButton,
    badge: String? = null,
) {
    val button = @Composable {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = containerColor,
            modifier = Modifier.size(Dimens.inviteTargetSize),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = tint,
                    modifier = Modifier.size(Dimens.iconMd),
                )
            }
        }
    }
    if (badge != null) {
        BadgedBox(badge = { Badge { Text(badge) } }) { button() }
    } else {
        button()
    }
}
