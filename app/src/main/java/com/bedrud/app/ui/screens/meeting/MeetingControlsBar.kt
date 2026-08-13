package com.bedrud.app.ui.screens.meeting

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.bedrud.app.R
import com.bedrud.app.core.audio.MeetingInputMode
import com.bedrud.app.ui.theme.BedrudShapeTokens
import com.bedrud.app.ui.theme.Dimens
import com.bedrud.app.ui.theme.Elevation
import kotlin.math.sin

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
    micLevelProvider: () -> Float = { 0f },
    voiceGateOpenProvider: () -> Boolean = { true },
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
            .padding(horizontal = Dimens.space16)
            .fillMaxWidth()
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
                color = colors.onButtonVariant.copy(alpha = DragHandleAlpha),
                onClick = onOpenMoreOptions,
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
                micLevelProvider = micLevelProvider,
                voiceGateOpenProvider = voiceGateOpenProvider,
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

/**
 * The five call controls, shared verbatim by the floating bar and the more-options sheet so both
 * places render the exact same buttons — sizes, off-state fills, badge, and the push-to-talk
 * pill included.
 */
@Composable
internal fun MeetingCallControlsRow(
    isMicEnabled: Boolean,
    isCameraEnabled: Boolean,
    micHasError: Boolean,
    cameraHasError: Boolean,
    isScreenShareEnabled: Boolean,
    showChat: Boolean,
    unreadCount: Int,
    inputMode: MeetingInputMode,
    micLevelProvider: () -> Float = { 0f },
    voiceGateOpenProvider: () -> Boolean = { true },
    onPushToTalkChange: (Boolean) -> Unit,
    onToggleMic: () -> Unit,
    onToggleCamera: () -> Unit,
    onToggleScreenShare: () -> Unit,
    onToggleChat: () -> Unit,
    onEndCall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = meetingChromeColors()
    // Three sections with equal-weight sides keep the mic slot — button or pill — exactly
    // centered under the drag handle, whatever the input mode, at a constant bar width.
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Side clusters anchor to the bar's edges so every control keeps its position across
        // input modes; only the space beside the centered mic slot absorbs the difference.
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            Row(
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
            }
        }

        MicPill(
            colors = colors,
            inputMode = inputMode,
            isMicEnabled = isMicEnabled,
            hasError = micHasError,
            micLevelProvider = micLevelProvider,
            voiceGateOpenProvider = voiceGateOpenProvider,
            onToggleMic = onToggleMic,
            onPushToTalkChange = onPushToTalkChange,
        )

        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.meetingBarItemGap),
            ) {
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
            // Clip before clickable so the press ripple follows the shape instead of
            // splashing as a rectangle.
            .clip(BedrudShapeTokens.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = Dimens.space16)
            .padding(top = Dimens.space6, bottom = Dimens.space6)
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
 * The mic slot, one pill for both input modes so the bar's rhythm never changes:
 * voice activity renders filled ("Open Mic", media-off fill while muted, tap toggles), and
 * push-to-talk renders outlined ("Push to Talk") until held, when it fills active ("Talking…").
 */
@Composable
private fun MicPill(
    colors: MeetingChromeColors,
    inputMode: MeetingInputMode,
    isMicEnabled: Boolean,
    hasError: Boolean,
    micLevelProvider: () -> Float,
    voiceGateOpenProvider: () -> Boolean,
    onToggleMic: () -> Unit,
    onPushToTalkChange: (Boolean) -> Unit,
) {
    val isPushToTalk = inputMode == MeetingInputMode.PUSH_TO_TALK
    val transmitting = isPushToTalk && isMicEnabled
    val containerColor = when {
        transmitting -> colors.buttonActive
        isPushToTalk -> Color.Transparent
        isMicEnabled -> colors.button
        else -> colors.buttonMediaOff
    }
    val contentColor = when {
        transmitting -> colors.onButton
        isPushToTalk -> colors.onButtonVariant
        isMicEnabled -> colors.onButton
        else -> colors.onButtonMediaOff
    }
    val gestureModifier = if (isPushToTalk) {
        Modifier.pointerInput(Unit) {
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
        }
    } else {
        Modifier.clickable(onClick = onToggleMic)
    }

    Box {
        Surface(
            shape = BedrudShapeTokens.pill,
            color = containerColor,
            border = if (isPushToTalk && !transmitting) {
                androidx.compose.foundation.BorderStroke(Dimens.borderThin, colors.divider)
            } else {
                null
            },
            modifier = Modifier
                .height(Dimens.meetingMediaButtonHeight)
                .widthIn(max = Dimens.meetingMicPillMaxWidth)
                // Clip before the gesture modifier so the press ripple follows the pill
                // instead of splashing as a rectangle.
                .clip(BedrudShapeTokens.pill)
                .then(gestureModifier),
        ) {
            // Every label is laid out invisibly so the pill keeps one width across modes and
            // states — nothing in the bar may move when the mode or hold state changes.
            Box(
                modifier = Modifier.padding(horizontal = Dimens.space12),
                contentAlignment = Alignment.CenterStart,
            ) {
                PillContent(
                    contentColor = Color.Transparent,
                    icon = Icons.Default.Mic,
                    textRes = R.string.meeting_ptt_openMic,
                    modifier = Modifier.alpha(0f),
                )
                PillContent(
                    contentColor = Color.Transparent,
                    icon = Icons.Default.Mic,
                    textRes = R.string.meeting_audio_mode_pushToTalk,
                    modifier = Modifier.alpha(0f),
                )
                PillContent(
                    contentColor = Color.Transparent,
                    icon = Icons.Default.Mic,
                    textRes = R.string.meeting_ptt_talking,
                    modifier = Modifier.alpha(0f),
                )
                // The mic glyph becomes a live meter while audio is actually being captured —
                // same slot, so the pill's width never moves.
                val micOpen = transmitting || (!isPushToTalk && isMicEnabled)
                PillContent(
                    contentColor = contentColor,
                    icon = if (micOpen) Icons.Default.Mic else Icons.Default.MicOff,
                    textRes = when {
                        transmitting -> R.string.meeting_ptt_talking
                        isPushToTalk -> R.string.meeting_audio_mode_pushToTalk
                        else -> R.string.meeting_ptt_openMic
                    },
                    showMeter = micOpen,
                    micLevelProvider = micLevelProvider,
                    voiceGateOpenProvider = voiceGateOpenProvider,
                )
            }
        }

        if (hasError && !isPushToTalk) {
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
private fun PillContent(
    contentColor: Color,
    icon: ImageVector,
    textRes: Int,
    modifier: Modifier = Modifier,
    showMeter: Boolean = false,
    micLevelProvider: () -> Float = { 0f },
    voiceGateOpenProvider: () -> Boolean = { true },
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.space8),
    ) {
        if (showMeter) {
            MicLevelBars(
                levelProvider = micLevelProvider,
                gateOpenProvider = voiceGateOpenProvider,
                color = contentColor,
                modifier = Modifier.size(Dimens.meetingBarIconMedia),
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = stringResource(R.string.meeting_contentDescription_toggleMic),
                tint = contentColor,
                modifier = Modifier.size(Dimens.meetingBarIconMedia),
            )
        }
        Text(
            text = stringResource(textRes),
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The live capture meter drawn in the mic slot: four bars whose height follows the microphone
 * level, sampled per animation frame from the capture processor (no state churn — the draw
 * scope reads the values). While the manual voice gate is closed the bars dim, so the
 * sensitivity threshold is visible rather than guessed.
 */
@Composable
private fun MicLevelBars(
    levelProvider: () -> Float,
    gateOpenProvider: () -> Boolean,
    color: Color,
    modifier: Modifier = Modifier,
) {
    var smoothedLevel by remember { mutableFloatStateOf(0f) }
    var phaseSeconds by remember { mutableFloatStateOf(0f) }
    var gateOpen by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { frameTimeNanos ->
                val target = levelProvider().coerceIn(0f, 1f)
                // Fast attack, slow decay: speech peaks register instantly, then glide down.
                val factor = if (target > smoothedLevel) MeterAttack else MeterDecay
                smoothedLevel += (target - smoothedLevel) * factor
                phaseSeconds = frameTimeNanos / NanosPerSecond
                gateOpen = gateOpenProvider()
            }
        }
    }

    Canvas(modifier = modifier) {
        val slot = size.width / (MeterBarCount * 2 - 1)
        val level = smoothedLevel
        val alpha = if (gateOpen) 1f else MeterClosedAlpha
        repeat(MeterBarCount) { index ->
            val wobble = sin(phaseSeconds * MeterWobbleSpeed + index * MeterWobblePhase)
            val scale = MeterBarScales[index] * (1f + wobble * MeterWobbleDepth * level)
            val height = (size.height * (MeterRestHeight + level * scale))
                .coerceIn(size.height * MeterRestHeight, size.height)
            drawRoundRect(
                color = color.copy(alpha = alpha),
                topLeft = Offset(index * slot * 2f, (size.height - height) / 2f),
                size = Size(slot, height),
                cornerRadius = CornerRadius(slot / 2f),
            )
        }
    }
}

// The bar's own pull-up handle, not a sheet's: the shared sheet scaffold fixes its handle, so this
// one carries its own tint. Matches the sheet handle's weight against the chrome.
private const val DragHandleAlpha = 0.55f

private const val MeterBarCount = 4
private val MeterBarScales = floatArrayOf(0.55f, 1f, 0.8f, 0.45f)
private const val MeterRestHeight = 0.18f
private const val MeterAttack = 0.45f
private const val MeterDecay = 0.12f
private const val MeterWobbleSpeed = 7f
private const val MeterWobblePhase = 1.7f
private const val MeterWobbleDepth = 0.35f
private const val MeterClosedAlpha = 0.35f
private const val NanosPerSecond = 1_000_000_000f

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
        shape = BedrudShapeTokens.pill,
        color = colors.endCall,
        modifier = Modifier.size(
            width = Dimens.meetingEndCallWidth,
            height = Dimens.meetingMediaButtonHeight,
        ),
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
