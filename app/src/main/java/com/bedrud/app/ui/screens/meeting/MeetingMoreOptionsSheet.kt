package com.bedrud.app.ui.screens.meeting

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.HeadsetOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.bedrud.app.R
import com.bedrud.app.core.audio.MeetingInputMode
import com.bedrud.app.core.audio.MeetingVoiceAlert
import com.bedrud.app.core.livekit.ConnectionState
import com.bedrud.app.ui.components.BedrudBottomSheet
import com.bedrud.app.ui.components.BedrudSheetActionRow
import com.bedrud.app.ui.theme.BedrudShapeTokens
import com.bedrud.app.ui.theme.Dimens

/**
 * The pull-up options sheet behind the controls bar's drag handle. The call controls repeat along
 * its top so pulling the bar up never takes them away, then the room-level options follow as
 * standard sheet rows. Toggles keep the sheet open; navigation rows close it.
 */
@Composable
fun MeetingMoreOptionsSheet(
    isMicEnabled: Boolean,
    isCameraEnabled: Boolean,
    micHasError: Boolean = false,
    cameraHasError: Boolean = false,
    isScreenShareEnabled: Boolean,
    showChat: Boolean,
    unreadCount: Int,
    isDeafened: Boolean,
    hideAllIncomingVideo: Boolean,
    isRoomSettingsAvailable: Boolean,
    inputMode: MeetingInputMode = MeetingInputMode.VOICE_ACTIVITY,
    connectionState: ConnectionState = ConnectionState.CONNECTED,
    voiceAlert: MeetingVoiceAlert = MeetingVoiceAlert.None,
    onPushToTalkChange: (Boolean) -> Unit = {},
    onToggleMic: () -> Unit,
    onToggleCamera: () -> Unit,
    onToggleScreenShare: () -> Unit,
    onToggleChat: () -> Unit,
    onEndCall: () -> Unit,
    onToggleDeafen: () -> Unit,
    onToggleHideAllIncomingVideo: () -> Unit,
    onOpenAudioSettings: () -> Unit,
    onOpenNoiseSuppression: () -> Unit,
    onOpenRoomSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = meetingChromeColors()

    BedrudBottomSheet(onDismiss = onDismiss) {
        // The controls keep the bar around them. Dragged out of it, they were five loose shapes on
        // the sheet's surface — a rounded-rect camera, two circles, a wide mic pill and the hang-up —
        // with nothing holding the variety together and `button` barely separating from the sheet's
        // own container. Same corner, fill and hairline as the collapsed bar, so expanding reveals
        // the options *below the bar you were already looking at* instead of restyling it mid-drag.
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.meetingScreenMargin, vertical = Dimens.space8),
            shape = BedrudShapeTokens.controlsBar,
            color = colors.bar,
            border = BorderStroke(Dimens.borderThin, colors.divider),
        ) {
            MeetingCallControlsRow(
                isMicEnabled = isMicEnabled,
                isCameraEnabled = isCameraEnabled,
                micHasError = micHasError,
                cameraHasError = cameraHasError,
                isScreenShareEnabled = isScreenShareEnabled,
                showChat = showChat,
                unreadCount = unreadCount,
                inputMode = inputMode,
                connectionState = connectionState,
                voiceAlert = voiceAlert,
                onPushToTalkChange = onPushToTalkChange,
                onToggleMic = onToggleMic,
                onToggleCamera = onToggleCamera,
                onToggleScreenShare = onToggleScreenShare,
                onToggleChat = {
                    onToggleChat()
                    onDismiss()
                },
                onEndCall = {
                    onDismiss()
                    onEndCall()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = Dimens.meetingBarPaddingH,
                        vertical = Dimens.meetingBarPaddingV,
                    ),
            )
        }

        // Toggles, not navigation: the accent tint plus a trailing check carries the state (the
        // sheet's selection language, same as the output picker) and the sheet stays open so the
        // flip is visible.
        BedrudSheetActionRow(
            // The same crossed headphone the tile badge wears: deafening is about what reaches your
            // ears, and a speaker icon says something about the room instead.
            icon = if (isDeafened) Icons.Default.HeadsetOff else Icons.Default.Headset,
            title = stringResource(R.string.meeting_sheet_deafen),
            contentColor = if (isDeafened) colors.accent else colors.onButton,
            trailing = {
                if (isDeafened) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(Dimens.iconSm),
                    )
                }
            },
            onClick = onToggleDeafen,
        )
        BedrudSheetActionRow(
            icon = if (hideAllIncomingVideo) Icons.Default.VideocamOff else Icons.Default.Videocam,
            title = stringResource(R.string.meeting_sheet_disableAllCameras),
            supportingText = stringResource(R.string.meeting_sheet_disableAllCamerasDescription),
            contentColor = if (hideAllIncomingVideo) colors.accent else colors.onButton,
            supportingColor = colors.onButtonVariant,
            trailing = {
                if (hideAllIncomingVideo) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(Dimens.iconSm),
                    )
                }
            },
            onClick = onToggleHideAllIncomingVideo,
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
