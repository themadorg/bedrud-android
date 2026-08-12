package com.bedrud.app.ui.screens.meeting

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ScreenShare
import androidx.compose.material.icons.automirrored.filled.StopScreenShare
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.bedrud.app.R
import com.bedrud.app.core.audio.MeetingInputMode
import com.bedrud.app.ui.theme.BedrudShapeTokens
import com.bedrud.app.ui.theme.Dimens
import com.bedrud.app.ui.theme.Elevation

/**
 * The floating in-call controls pill: camera, screen share, mic, chat and hang-up, with a drag
 * handle on top. Tapping the handle — or swiping up anywhere on the bar — opens the more-options
 * sheet, mirroring how a bottom sheet is pulled up. The sheet itself is owned by the caller.
 */
@Composable
fun MeetingControlsBar(
    isMicEnabled: Boolean,
    isCameraEnabled: Boolean,
    micHasError: Boolean = false,
    cameraHasError: Boolean = false,
    isScreenShareEnabled: Boolean,
    showChat: Boolean,
    unreadCount: Int,
    inputMode: MeetingInputMode = MeetingInputMode.VOICE_ACTIVITY,
    onPushToTalkChange: (Boolean) -> Unit = {},
    onToggleMic: () -> Unit,
    onToggleCamera: () -> Unit,
    onToggleScreenShare: () -> Unit,
    onToggleChat: () -> Unit,
    onOpenMoreOptions: () -> Unit,
    onEndCall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = meetingChromeColors()
    val swipeThresholdPx = with(LocalDensity.current) {
        Dimens.meetingHandleSwipeThreshold.toPx()
    }

    Surface(
        modifier = modifier
            .navigationBarsPadding()
            .pointerInput(Unit) {
                var dragTotal = 0f
                detectVerticalDragGestures(
                    onDragStart = { dragTotal = 0f },
                    onVerticalDrag = { _, dragAmount -> dragTotal += dragAmount },
                    onDragEnd = {
                        if (dragTotal < -swipeThresholdPx) onOpenMoreOptions()
                    },
                )
            },
        shape = BedrudShapeTokens.controlsBar,
        color = colors.bar,
        shadowElevation = Elevation.controlsBarShadow,
        tonalElevation = Elevation.controlsBarTonal,
        border = androidx.compose.foundation.BorderStroke(Dimens.borderThin, colors.divider),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            DragHandle(
                color = colors.dragHandle,
                onClick = onOpenMoreOptions,
            )

            Row(
                modifier = Modifier.padding(
                    start = Dimens.meetingBarPaddingH,
                    end = Dimens.meetingBarPaddingH,
                    bottom = Dimens.meetingBarPaddingV,
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.meetingBarItemGap),
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

                if (inputMode == MeetingInputMode.PUSH_TO_TALK) {
                    HoldToTalkPill(
                        colors = colors,
                        transmitting = isMicEnabled,
                        onPushToTalkChange = onPushToTalkChange,
                    )
                } else {
                    MeetMediaButton(
                        colors = colors,
                        enabled = isMicEnabled,
                        hasError = micHasError,
                        onClick = onToggleMic,
                        enabledIcon = Icons.Default.Mic,
                        disabledIcon = Icons.Default.MicOff,
                        contentDescription = stringResource(R.string.meeting_contentDescription_toggleMic),
                    )
                }

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

                MeetEndCallButton(colors = colors, onClick = onEndCall)
            }
        }
    }
}

/**
 * The pull-up affordance above the controls. Sized like the M3 sheet drag handle, wrapped in a
 * larger clickable area so it is also a tap target, with the more-options semantics.
 */
@Composable
private fun DragHandle(
    color: Color,
    onClick: () -> Unit,
) {
    val description = stringResource(R.string.meeting_contentDescription_moreOptions)
    Box(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = Dimens.space16, vertical = Dimens.space6)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(Dimens.meetingHandleWidth)
                .height(Dimens.meetingHandleHeight)
                .background(color, CircleShape),
        )
    }
}

/**
 * Push-to-talk control: the mic slot widens into a pill that transmits only while held —
 * outlined while idle, filled while talking, so the state needs no color code to learn.
 */
@Composable
private fun HoldToTalkPill(
    colors: MeetingChromeColors,
    transmitting: Boolean,
    onPushToTalkChange: (Boolean) -> Unit,
) {
    Surface(
        shape = BedrudShapeTokens.pill,
        color = if (transmitting) colors.buttonActive else colors.buttonMediaOff,
        border = if (transmitting) {
            null
        } else {
            androidx.compose.foundation.BorderStroke(Dimens.borderThin, colors.divider)
        },
        modifier = Modifier
            .height(Dimens.meetingMediaButtonHeight)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onPushToTalkChange(true)
                        try {
                            awaitRelease()
                        } finally {
                            onPushToTalkChange(false)
                        }
                    },
                )
            },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Dimens.space16),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.space8),
        ) {
            Icon(
                imageVector = if (transmitting) Icons.Default.Mic else Icons.Default.MicOff,
                contentDescription = stringResource(R.string.meeting_contentDescription_toggleMic),
                tint = if (transmitting) colors.onButton else colors.onButtonMediaOff,
                modifier = Modifier.size(Dimens.meetingBarIconMedia),
            )
            Text(
                text = stringResource(
                    if (transmitting) R.string.meeting_ptt_talking
                    else R.string.meeting_ptt_holdToTalk
                ),
                style = MaterialTheme.typography.labelLarge,
                color = if (transmitting) colors.onButton else colors.onButtonMediaOff,
                maxLines = 1,
            )
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
    Box(
        modifier = Modifier.size(
            width = Dimens.meetingMediaButtonWidth,
            height = Dimens.meetingMediaButtonHeight,
        ),
    ) {
        Surface(
            onClick = onClick,
            shape = BedrudShapeTokens.field,
            color = if (enabled) colors.button else colors.buttonMediaOff,
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (enabled) enabledIcon else disabledIcon,
                    contentDescription = contentDescription,
                    tint = if (enabled) colors.onButton else colors.onButtonMediaOff,
                    modifier = Modifier.size(Dimens.meetingBarIconMedia),
                )
            }
        }

        if (hasError) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = Dimens.space4, y = -Dimens.space4)
                    .size(Dimens.iconXs)
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
            modifier = Modifier.size(Dimens.meetingCircleButton),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = colors.onButton,
                    modifier = Modifier.size(Dimens.meetingBarIconSm),
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
        modifier = Modifier.size(Dimens.meetingEndCallButton),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.CallEnd,
                contentDescription = stringResource(R.string.meeting_contentDescription_leaveCall),
                tint = colors.onEndCall,
                modifier = Modifier.size(Dimens.meetingBarIconLg),
            )
        }
    }
}
