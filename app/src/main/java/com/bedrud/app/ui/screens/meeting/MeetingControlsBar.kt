package com.bedrud.app.ui.screens.meeting

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import com.bedrud.app.R
import com.bedrud.app.core.audio.MeetingInputMode
import com.bedrud.app.core.audio.MeetingVoiceAlert
import com.bedrud.app.core.livekit.ConnectionState
import com.bedrud.app.ui.theme.BedrudShapeTokens
import com.bedrud.app.ui.theme.Dimens
import com.bedrud.app.ui.theme.Elevation
import com.bedrud.app.ui.theme.Motion
import com.bedrud.app.ui.theme.bedrudColors
import kotlin.math.PI
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
    connectionState: ConnectionState = ConnectionState.CONNECTED,
    voiceAlert: MeetingVoiceAlert = MeetingVoiceAlert.None,
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
            .padding(horizontal = Dimens.meetingScreenMargin)
            .fillMaxWidth()
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
    connectionState: ConnectionState = ConnectionState.CONNECTED,
    voiceAlert: MeetingVoiceAlert = MeetingVoiceAlert.None,
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
            connectionState = connectionState,
            voiceAlert = voiceAlert,
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
            .padding(top = Dimens.space4, bottom = Dimens.space4)
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
 * voice activity renders filled ("Speak", "Muted" and a media-off fill while muted, tap toggles), and
 * push-to-talk renders outlined ("Push to Talk") until held, when it fills active ("Talking…").
 */
@Composable
private fun MicPill(
    colors: MeetingChromeColors,
    inputMode: MeetingInputMode,
    isMicEnabled: Boolean,
    hasError: Boolean,
    connectionState: ConnectionState,
    voiceAlert: MeetingVoiceAlert,
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
    // Pressed state comes from two places because the pill is held in one mode and tapped in the
    // other; the key sinks the same way for both.
    val interactionSource = remember { MutableInteractionSource() }
    val isTapped by interactionSource.collectIsPressedAsState()
    var isHeld by remember { mutableStateOf(false) }
    val isPressed = isTapped || isHeld
    val gestureModifier = if (isPushToTalk) {
        Modifier.pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    isHeld = true
                    onPushToTalkChange(true)
                    try {
                        awaitRelease()
                    } finally {
                        isHeld = false
                        onPushToTalkChange(false)
                    }
                },
            )
        }
    } else {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            onClick = onToggleMic,
        )
    }

    // A shadow under a transparent container would show straight through it, so the idle
    // push-to-talk pill — which is only an outline — stays flat and leans on its border instead.
    val raised = containerColor != Color.Transparent
    val pillElevation by animateDpAsState(
        targetValue = when {
            !raised -> Elevation.level0
            isPressed -> Elevation.micPillPressed
            else -> Elevation.micPillResting
        },
        animationSpec = tween(Motion.durationShort, easing = Motion.standardEasing),
        label = "micPillElevation",
    )
    val pillScale by animateFloatAsState(
        targetValue = if (isPressed) MicPillPressedScale else 1f,
        animationSpec = tween(Motion.durationShort, easing = Motion.standardEasing),
        label = "micPillScale",
    )

    // The pill's own outline reports anything stopping your voice reaching the room, so the
    // status sits on the control that fixes it instead of in a banner elsewhere on screen.
    val status = when {
        connectionState == ConnectionState.RECONNECTING -> MicPillStatus.Reconnecting
        voiceAlert != MeetingVoiceAlert.None -> MicPillStatus.VoiceBlocked
        else -> MicPillStatus.None
    }
    val statusLabel = when (status) {
        MicPillStatus.None -> null
        MicPillStatus.Reconnecting -> stringResource(R.string.meeting_status_reconnecting)
        MicPillStatus.VoiceBlocked -> stringResource(voiceAlert.messageRes())
    }

    Box(
        modifier = Modifier
            // Scaling the whole box keeps the status ring welded to the key it belongs to.
            .graphicsLayer {
                scaleX = pillScale
                scaleY = pillScale
            }
            .semantics {
                statusLabel?.let { stateDescription = it }
            },
    ) {
        Surface(
            shape = BedrudShapeTokens.pill,
            color = containerColor,
            shadowElevation = pillElevation,
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
            // Centred, not start-aligned. The pill is as wide as its longest label ever gets, so
            // start-aligning left "Muted" and "Speak" hard against the icon with a pill's worth of
            // empty space trailing them — the control looked broken rather than compact. Centring
            // costs a small horizontal shift when the label itself changes length, which is a far
            // smaller thing to notice than a permanently lopsided pill.
            Box(
                modifier = Modifier.padding(horizontal = Dimens.space12),
                contentAlignment = Alignment.Center,
            ) {
                PillContent(
                    contentColor = Color.Transparent,
                    icon = Icons.Default.Mic,
                    textRes = R.string.meeting_ptt_speak,
                    modifier = Modifier.alpha(0f),
                )
                PillContent(
                    contentColor = Color.Transparent,
                    icon = Icons.Default.Mic,
                    textRes = R.string.meeting_ptt_muted,
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
                // Whether the room hears you is the tile ring's job now, so the pill only says
                // whether the microphone is open.
                val micOpen = transmitting || (!isPushToTalk && isMicEnabled)
                PillContent(
                    contentColor = contentColor,
                    icon = if (micOpen) Icons.Default.Mic else Icons.Default.MicOff,
                    // The label said "Open Mic" whether or not the mic was open: muted, it showed a
                    // crossed-out microphone next to the words for the opposite state, and the icon
                    // was the only thing telling the truth. Push to talk keeps naming its mode --
                    // not holding the key is not the same as being muted.
                    textRes = when {
                        transmitting -> R.string.meeting_ptt_talking
                        isPushToTalk -> R.string.meeting_audio_mode_pushToTalk
                        isMicEnabled -> R.string.meeting_ptt_speak
                        else -> R.string.meeting_ptt_muted
                    },
                )
            }
        }

        // Fades in and out rather than appearing and vanishing. The ring is drawn while it is
        // still fading, so the status it is showing has to outlive the status going away.
        var fadingStatus by remember { mutableStateOf(status) }
        LaunchedEffect(status) {
            if (status != MicPillStatus.None) fadingStatus = status
        }
        val ringPresence by animateFloatAsState(
            targetValue = if (status == MicPillStatus.None) 0f else 1f,
            animationSpec = tween(Motion.meetingMicRingFadeMs, easing = Motion.standardEasing),
            label = "micRingPresence",
        )
        if (ringPresence > 0f) {
            MicStatusRing(
                status = fadingStatus,
                presence = ringPresence,
                modifier = Modifier.matchParentSize(),
            )
        }

        if (hasError && !isPushToTalk) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = Dimens.space4, y = -Dimens.space4)
                    .size(Dimens.iconXs)
                    .background(colors.mediaError, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "!",
                    color = colors.onMediaError,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

/** What the mic pill's outline is reporting, if anything. */
private enum class MicPillStatus { None, Reconnecting, VoiceBlocked }

/**
 * The amber ring around the mic pill.
 *
 * Both states are the same colour because they are the same news — your voice is not reaching the
 * room — so the motion is what separates the cause. A reconnect sends a single arc travelling
 * round the outline, the shape a progress indicator makes when it does not know how long it will
 * take. Anything you can fix yourself (muted, push-to-talk not held, the gate shut) pulses in
 * place instead: stationary, because nothing is in progress and the fix is under your thumb.
 *
 * There is no "connecting" case here. The first connect happens behind a full-screen state, and
 * this bar does not exist yet when it does.
 *
 * [presence] scales the whole ring so it can fade on and off instead of blinking into existence.
 */
@Composable
private fun MicStatusRing(
    status: MicPillStatus,
    presence: Float,
    modifier: Modifier = Modifier,
) {
    val ringColor = MaterialTheme.bedrudColors.warning
    val transition = rememberInfiniteTransition(label = "micStatusRing")
    val travel by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(Motion.meetingMicRingTravelMs, easing = LinearEasing),
        ),
        label = "micRingTravel",
    )
    val pulse by transition.animateFloat(
        initialValue = MicRingPulseMinAlpha,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(Motion.meetingMicRingPulseMs, easing = Motion.standardEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "micRingPulse",
    )
    val strokeWidth = with(LocalDensity.current) { Dimens.meetingMicRingStroke.toPx() }

    Canvas(modifier = modifier) {
        val corner = CornerRadius(size.height / 2f)
        val inset = strokeWidth / 2f
        val outline = Size(size.width - strokeWidth, size.height - strokeWidth)
        // A dashed stroke with one arc and one gap the length of the rest of the outline: shifting
        // the phase walks that single arc around the pill, and it follows the rounded corners for
        // free where a rotated sweep gradient would have squashed the shape.
        //
        // This has to be the *stroke path's* length, not the bounding box's. A pill's corners are
        // quarter-circles, so its outline is shorter than 2(w+h) — with the box figure the dash
        // pattern does not divide into the path and the arc jumps every time the phase wraps,
        // which reads as a stutter at the start and end of each lap. Straight runs are the two
        // sides minus a full corner diameter each; the four corners sum to one circle.
        val radius = outline.height / 2f
        val perimeter =
            2f * (outline.width - outline.height).coerceAtLeast(0f) + TwoPi * radius
        val arc = perimeter * MicRingArcFraction
        val stroke = if (status == MicPillStatus.Reconnecting) {
            Stroke(
                width = strokeWidth,
                pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(arc, perimeter - arc),
                    phase = -travel * perimeter,
                ),
            )
        } else {
            Stroke(width = strokeWidth)
        }
        drawRoundRect(
            color = ringColor,
            topLeft = Offset(inset, inset),
            size = outline,
            cornerRadius = corner,
            style = stroke,
            alpha = presence * if (status == MicPillStatus.Reconnecting) 1f else pulse,
        )
    }
}

/** Maps a voice alert onto the wording the pill reports to accessibility services. */
private fun MeetingVoiceAlert.messageRes(): Int = when (this) {
    MeetingVoiceAlert.Muted -> R.string.meeting_voiceAlert_muted
    MeetingVoiceAlert.PushToTalkIdle -> R.string.meeting_voiceAlert_pushToTalk
    MeetingVoiceAlert.GateClosed -> R.string.meeting_voiceAlert_gateClosed
    MeetingVoiceAlert.NotReachingRoom, MeetingVoiceAlert.None ->
        R.string.meeting_voiceAlert_notReachingRoom
}

/** How faint the pulse gets at its dimmest — still visible, so the ring never seems to vanish. */
private const val MicRingPulseMinAlpha = 0.3f

/** Share of the pill's outline the travelling arc covers. */
private const val MicRingArcFraction = 0.28f

/** How far the mic key shrinks under a press — enough to feel, too little to jostle the bar. */
private const val MicPillPressedScale = 0.96f

/** One full turn, for the pill outline's corner arcs. */
private const val TwoPi = (2.0 * PI).toFloat()

@Composable
private fun PillContent(
    contentColor: Color,
    icon: ImageVector,
    textRes: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.space8),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = stringResource(R.string.meeting_contentDescription_toggleMic),
            tint = contentColor,
            modifier = Modifier.size(Dimens.meetingBarIconMedia),
        )
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
internal fun MicLevelBars(
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
                    .background(colors.mediaError, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "!",
                    color = colors.onMediaError,
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
