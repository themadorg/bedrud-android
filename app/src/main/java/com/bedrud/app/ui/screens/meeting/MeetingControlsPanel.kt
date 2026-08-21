package com.bedrud.app.ui.screens.meeting

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
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
 *
 * Chat lives here too, as the same movement at a larger size: open it and the conversation
 * ([chatConversation]) grows out of the bar the way the options do, while the bar's own row
 * morphs slot-by-slot into the composer — camera to "+", mic pill to field, hang-up to send.
 * One surface throughout, so chat is visibly something the call bar *does*, not a second screen
 * dealt over it. The panel and the keyboard share a window now, which is what lets the bar ride
 * up over the IME while the call stays where it is.
 */
@Composable
fun BoxScope.MeetingControlsPanel(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    chatOpen: Boolean,
    onChatOpenChange: (Boolean) -> Unit,
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
    chatInput: String,
    onChatInputChange: (String) -> Unit,
    onSendChat: () -> Unit,
    chatComposer: MeetingChatComposerState,
    @StringRes sendDisabledReason: Int?,
    chatConversation: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = meetingChromeColors()
    val collapse = { onExpandedChange(false) }
    val closeChat = { onChatOpenChange(false) }
    val swipeThresholdPx = with(LocalDensity.current) {
        Dimens.meetingHandleSwipeThreshold.toPx()
    }

    BackHandler(enabled = chatOpen, onBack = closeChat)
    BackHandler(enabled = expanded, onBack = collapse)

    // The keyboard follows the field; it must not stay up pointing at a bar that has already
    // become the call controls again. A reported upload failure is put away on the same edge —
    // the composer state outlives the panel, and stale bad news must not greet the next open.
    val focusManager = LocalFocusManager.current
    LaunchedEffect(chatOpen) {
        if (!chatOpen) {
            focusManager.clearFocus()
            chatComposer.dismissError()
        }
    }

    // One scrim for both panels. Its clock follows whichever is moving — and on the way out,
    // whichever *was* open — so the dim and the panel always read as one movement rather than a
    // fade that finishes early and leaves the panel travelling on its own.
    var scrimForChat by remember { mutableStateOf(false) }
    LaunchedEffect(chatOpen, expanded) {
        if (chatOpen) scrimForChat = true else if (expanded) scrimForChat = false
    }
    val overlayOpen = expanded || chatOpen
    val scrimDurationMs = when {
        chatOpen -> Motion.meetingChatExpandMs
        expanded -> Motion.meetingOptionsExpandMs
        scrimForChat -> Motion.meetingChatCollapseMs
        else -> Motion.meetingOptionsCollapseMs
    }
    val scrimAlpha by animateFloatAsState(
        targetValue = if (overlayOpen) ScrimAlpha else 0f,
        animationSpec = tween(durationMillis = scrimDurationMs, easing = Motion.standardEasing),
        label = "optionsScrimAlpha",
    )
    if (scrimAlpha > 0f) {
        val closeLabel = stringResource(
            if (chatOpen) R.string.meeting_contentDescription_toggleChat
            else R.string.meeting_contentDescription_moreOptions,
        )
        val dismiss = { if (chatOpen) closeChat() else collapse() }
        // Interactive only while something is actually open: the dim lingers for its fade-out,
        // and a scrim that kept swallowing taps through it would eat the first tap after every
        // close — the exact tap-eater the platform sheet was left for.
        val interaction = if (overlayOpen) {
            Modifier
                .pointerInput(expanded, chatOpen) { detectTapGestures { dismiss() } }
                .semantics {
                    role = Role.Button
                    contentDescription = closeLabel
                    onClick { dismiss(); true }
                }
        } else {
            Modifier
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = scrimAlpha))
                .then(interaction),
        )
    }

    MeetingBarSurface(
        // The IME inset is the panel's own: the conversation and the composer live in the call's
        // window now, so the whole panel rides up over the keyboard while the call stays put.
        modifier = modifier.imePadding().pointerInput(expanded, chatOpen) {
            var dragTotal = 0f
            detectVerticalDragGestures(
                onDragStart = { dragTotal = 0f },
                onVerticalDrag = { _, dragAmount -> dragTotal += dragAmount },
                // Up opens, down closes — the panel answers the same gesture in both
                // directions, so whatever opened it puts it away again.
                onDragEnd = {
                    if (chatOpen) {
                        if (dragTotal > swipeThresholdPx) closeChat()
                    } else {
                        if (!expanded && dragTotal < -swipeThresholdPx) onExpandedChange(true)
                        if (expanded && dragTotal > swipeThresholdPx) collapse()
                    }
                },
            )
        },
    ) {
        // The conversation cannot sit straight on the bar's own fill: a bubble from someone else
        // is that same colour, and on it the message dissolves into the panel. While chat is open
        // the region above the row — handle included, the way the old sheet carried its handle —
        // recedes to `surfaceContainerLow`, so the bubbles have a ground to stand off. Animated on
        // the reveal's own clock, or the strip would snap while the panel is still growing.
        val conversationGround by animateColorAsState(
            targetValue = if (chatOpen) {
                MaterialTheme.colorScheme.surfaceContainerLow
            } else {
                colors.bar
            },
            animationSpec = tween(
                durationMillis = if (chatOpen) Motion.meetingChatExpandMs else Motion.meetingChatCollapseMs,
                easing = Motion.standardEasing,
            ),
            label = "conversationGround",
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(conversationGround),
                contentAlignment = Alignment.Center,
            ) {
                MeetingPanelHandle(
                    color = colors.onButtonVariant,
                    description = stringResource(
                        if (chatOpen) R.string.meeting_contentDescription_toggleChat
                        else R.string.meeting_contentDescription_moreOptions,
                    ),
                    onClick = { if (chatOpen) closeChat() else onExpandedChange(!expanded) },
                )
            }

            AnimatedVisibility(
                visible = expanded && !chatOpen,
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

            // The conversation: the options' movement at a larger size. It grows from the same
            // bottom edge, above the row it belongs to, on a slightly longer clock because it
            // travels several times the height.
            AnimatedVisibility(
                visible = chatOpen,
                // Weighted with fill off, so the conversation is measured LAST and takes only
                // what the handle and the bar's row leave behind. Measured in document order it
                // helped itself to a fixed share first, and in a short window — landscape with
                // the keyboard up — the row was what got squeezed, its 48dp controls compressed
                // below their touch size. The row's height is the bar's identity; the
                // conversation is the part that can afford to shrink.
                modifier = Modifier.weight(1f, fill = false),
                enter = expandVertically(
                    animationSpec = tween(Motion.meetingChatExpandMs, easing = Motion.standardEasing),
                ) + fadeIn(
                    animationSpec = tween(Motion.meetingChatExpandMs, easing = Motion.standardEasing),
                ),
                exit = shrinkVertically(
                    animationSpec = tween(Motion.meetingChatCollapseMs, easing = Motion.standardEasing),
                ) + fadeOut(
                    animationSpec = tween(Motion.meetingChatCollapseMs, easing = Motion.standardEasing),
                ),
            ) {
                // A share of the height the keyboard leaves, not all of it: the call stays in
                // view above the panel, which is the point of chat being a panel and not a page.
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(maxHeight * ChatPanelHeightFraction)
                            // The same ground as the handle strip above, so the recessed region
                            // reads as one piece from the panel's top edge down to the row.
                            .background(conversationGround),
                    ) {
                        chatConversation()
                    }
                }
            }

            MeetingCallControlsRow(
                chatOpen = chatOpen,
                isMicEnabled = isMicEnabled,
                isCameraEnabled = isCameraEnabled,
                micHasError = micHasError,
                cameraHasError = cameraHasError,
                isScreenShareEnabled = isScreenShareEnabled,
                showChat = showChat,
                unreadCount = unreadCount,
                inputMode = inputMode,
                chatInput = chatInput,
                onChatInputChange = onChatInputChange,
                onSendChat = onSendChat,
                chatComposer = chatComposer,
                sendDisabledReason = sendDisabledReason,
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

/**
 * The conversation's share of the height the keyboard leaves.
 *
 * Enough to read a real stretch of the conversation; short of enough to stop the call reading as
 * the thing behind it. The old sheet rested at half the window — this sits a little taller because
 * the bar below it is part of the same surface now, not a second object under a separate one.
 */
private const val ChatPanelHeightFraction = 0.6f

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
