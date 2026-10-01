package com.bedrud.app.ui.screens.meeting

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.HeadsetOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import com.bedrud.app.R
import com.bedrud.app.core.audio.MeetingInputMode
import com.bedrud.app.core.audio.MeetingVoiceAlert
import com.bedrud.app.core.livekit.ConnectionState
import com.bedrud.app.ui.components.BedrudBottomSheet
import com.bedrud.app.ui.components.BedrudSheetActionRow
import com.bedrud.app.ui.components.BedrudSheetTitle
import com.bedrud.app.ui.theme.Dimens

/** The trailing tick an active toggle wears, in the call's selection language. */
@Composable
private fun SelectedCheck(visible: Boolean, tint: androidx.compose.ui.graphics.Color) {
    if (!visible) return
    Icon(
        imageVector = Icons.Default.Check,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(Dimens.iconSm),
    )
}

/**
 * The in-call controls, and the room options their handle opens.
 *
 * The options are an ordinary [BedrudBottomSheet], titled, like every other sheet in the app. They
 * used to grow out of the controls pill instead, which read as a custom surface beside the app's
 * sheets: floating, outlined, rounded on all sides and without a title. The sheet carries only the
 * options, never a copy of the five call controls — an earlier sheet did, and the same buttons then
 * appeared twice at two elevations.
 */
@Composable
fun BoxScope.MeetingControlsPanel(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
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
    modifier: Modifier = Modifier,
) {
    val colors = meetingChromeColors()
    val collapse = { onExpandedChange(false) }
    val swipeThresholdPx = with(LocalDensity.current) {
        Dimens.meetingHandleSwipeThreshold.toPx()
    }

    if (expanded) {
        // Scrim, drag-down, Back and the handle's own semantics all come from the sheet.
        BedrudBottomSheet(onDismiss = collapse) {
            BedrudSheetTitle(
                text = stringResource(R.string.meeting_sheet_moreOptions),
                color = colors.onButton,
            )
            // Toggles, not navigation: the accent tint plus a trailing check carries the state and
            // the sheet stays open so the flip is visible. Rows that lead somewhere else close it on
            // the way.
            BedrudSheetActionRow(
                // The same crossed headphone the tile badge wears: deafening is about what reaches
                // your ears, and a speaker icon says something about the room.
                icon = if (isDeafened) Icons.Default.HeadsetOff else Icons.Default.Headset,
                title = stringResource(R.string.meeting_sheet_deafen),
                contentColor = if (isDeafened) colors.accent else colors.onButton,
                trailing = { SelectedCheck(visible = isDeafened, tint = colors.accent) },
                onClick = onToggleDeafen,
            )
            BedrudSheetActionRow(
                icon = if (hideAllIncomingVideo) Icons.Default.VideocamOff else Icons.Default.Videocam,
                title = stringResource(R.string.meeting_sheet_disableAllCameras),
                supportingText = stringResource(R.string.meeting_sheet_disableAllCamerasDescription),
                contentColor = if (hideAllIncomingVideo) colors.accent else colors.onButton,
                supportingColor = colors.onButtonVariant,
                trailing = { SelectedCheck(visible = hideAllIncomingVideo, tint = colors.accent) },
                onClick = onToggleHideAllIncomingVideo,
            )
            BedrudSheetActionRow(
                icon = Icons.Default.Headset,
                title = stringResource(R.string.meeting_sheet_audioSettings),
                contentColor = colors.onButton,
                onClick = {
                    collapse()
                    onOpenAudioSettings()
                },
            )
            BedrudSheetActionRow(
                icon = Icons.Default.GraphicEq,
                title = stringResource(R.string.meeting_sheet_noiseSuppression),
                contentColor = colors.onButton,
                onClick = {
                    collapse()
                    onOpenNoiseSuppression()
                },
            )
            if (isRoomSettingsAvailable) {
                BedrudSheetActionRow(
                    icon = Icons.Default.Settings,
                    title = stringResource(R.string.meeting_sheet_roomSettings),
                    contentColor = colors.onButton,
                    onClick = {
                        collapse()
                        onOpenRoomSettings()
                    },
                )
            }
        }
    }

    MeetingBarSurface(
        modifier = modifier.pointerInput(Unit) {
            var dragTotal = 0f
            detectVerticalDragGestures(
                onDragStart = { dragTotal = 0f },
                onVerticalDrag = { _, dragAmount -> dragTotal += dragAmount },
                // Up opens. Putting the options away is the sheet's own drag down.
                onDragEnd = { if (dragTotal < -swipeThresholdPx) onExpandedChange(true) },
            )
        },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            MeetingPanelHandle(
                color = colors.onButtonVariant,
                onClick = { onExpandedChange(true) },
            )

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
                onToggleChat = onToggleChat,
                onEndCall = onEndCall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = Dimens.meetingBarPaddingH,
                        end = Dimens.meetingBarPaddingH,
                        bottom = Dimens.meetingBarPaddingV,
                    ),
            )
        }
    }
}
