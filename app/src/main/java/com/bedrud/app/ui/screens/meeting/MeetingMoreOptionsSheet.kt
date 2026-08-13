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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.bedrud.app.R
import com.bedrud.app.core.audio.MeetingInputMode
import com.bedrud.app.ui.components.BedrudBottomSheet
import com.bedrud.app.ui.components.BedrudSheetActionRow
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
    micLevelProvider: () -> Float = { 0f },
    voiceGateOpenProvider: () -> Boolean = { true },
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
        // The exact controls from the bar, pulled up with the sheet
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Dimens.space8),
            contentAlignment = Alignment.Center,
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
                micLevelProvider = micLevelProvider,
                voiceGateOpenProvider = voiceGateOpenProvider,
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
                modifier = Modifier.fillMaxWidth(),
            )
        }

        HorizontalDivider(color = colors.divider)

        // Toggles, not navigation: the accent tint plus a trailing check carries the state (the
        // sheet's selection language, same as the output picker) and the sheet stays open so the
        // flip is visible.
        BedrudSheetActionRow(
            icon = if (isDeafened) Icons.AutoMirrored.Filled.VolumeOff
            else Icons.AutoMirrored.Filled.VolumeUp,
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
