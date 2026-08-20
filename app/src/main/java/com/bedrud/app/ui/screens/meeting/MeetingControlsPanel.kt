package com.bedrud.app.ui.screens.meeting

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import com.bedrud.app.R
import com.bedrud.app.core.audio.MeetingInputMode
import com.bedrud.app.core.audio.MeetingVoiceAlert
import com.bedrud.app.core.livekit.ConnectionState
import com.bedrud.app.ui.components.BedrudSheetActionRow
import com.bedrud.app.ui.theme.Dimens
import com.bedrud.app.ui.theme.Motion

/** How far the scrim dims the call while the options are open. */
private const val ScrimAlpha = 0.32f

/**
 * The in-call controls, and the room options that grow out of them.
 *
 * This is deliberately **not** a [com.bedrud.app.ui.components.BedrudBottomSheet], the one place in
 * the app that departs from the sheet standard. As a sheet, the options arrived as a second surface
 * carrying its own copy of the controls, sliding up over the real bar and settling at a different
 * height: the same five buttons appeared twice, at two elevations, and the row your thumb was
 * resting on jumped. Here there is one surface. It is anchored to the bottom, so the options unfold
 * *above* the controls and the controls themselves never move — the pill simply becomes taller,
 * which is what the sheet only ever claimed to do.
 *
 * That anchoring is also why the options read bottom-up rather than top-down: the row you were
 * already touching stays the panel's floor, and everything new appears above it.
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

    BackHandler(enabled = expanded, onBack = collapse)

    // Same timing as the panel it dims for, so the two read as one movement rather than a fade
    // that finishes early and leaves the panel travelling on its own.
    val scrimAlpha by animateFloatAsState(
        targetValue = if (expanded) ScrimAlpha else 0f,
        animationSpec = tween(
            durationMillis = if (expanded) Motion.meetingOptionsExpandMs else Motion.meetingOptionsCollapseMs,
            easing = Motion.standardEasing,
        ),
        label = "optionsScrimAlpha",
    )
    if (scrimAlpha > 0f) {
        val closeLabel = stringResource(R.string.meeting_contentDescription_moreOptions)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = scrimAlpha))
                .pointerInput(expanded) { detectTapGestures { collapse() } }
                .semantics {
                    contentDescription = closeLabel
                    onClick { collapse(); true }
                },
        )
    }

    MeetingBarSurface(
        modifier = modifier.pointerInput(expanded) {
            var dragTotal = 0f
            detectVerticalDragGestures(
                onDragStart = { dragTotal = 0f },
                onVerticalDrag = { _, dragAmount -> dragTotal += dragAmount },
                // Up opens, down closes — the panel answers the same gesture in both
                // directions, so whatever opened it puts it away again.
                onDragEnd = {
                    if (!expanded && dragTotal < -swipeThresholdPx) onExpandedChange(true)
                    if (expanded && dragTotal > swipeThresholdPx) collapse()
                },
            )
        },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            MeetingPanelHandle(
                color = colors.onButtonVariant,
                onClick = { onExpandedChange(!expanded) },
            )

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(
                    animationSpec = tween(Motion.meetingOptionsExpandMs, easing = Motion.standardEasing),
                ) + fadeIn(
                    animationSpec = tween(Motion.meetingOptionsExpandMs, easing = Motion.standardEasing),
                ),
                exit = shrinkVertically(
                    animationSpec = tween(Motion.meetingOptionsCollapseMs, easing = Motion.standardEasing),
                ) + fadeOut(
                    animationSpec = tween(Motion.meetingOptionsCollapseMs, easing = Motion.standardEasing),
                ),
            ) {
                Column(modifier = Modifier.padding(horizontal = Dimens.space4)) {
                    // Toggles, not navigation: the accent tint plus a trailing check carries the
                    // state and the panel stays open so the flip is visible. Rows that lead
                    // somewhere else close it on the way.
                    BedrudSheetActionRow(
                        // The same crossed headphone the tile badge wears: deafening is about what
                        // reaches your ears, and a speaker icon says something about the room.
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
                    collapse()
                    onToggleChat()
                },
                onEndCall = {
                    collapse()
                    onEndCall()
                },
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

/** The trailing tick an active toggle wears, in the panel's selection language. */
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
