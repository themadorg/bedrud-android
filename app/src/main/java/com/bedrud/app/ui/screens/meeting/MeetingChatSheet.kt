package com.bedrud.app.ui.screens.meeting

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ime
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import com.bedrud.app.core.livekit.ChatAttachment
import com.bedrud.app.core.livekit.ChatMessage
import com.bedrud.app.core.meeting.chat.ChatPoll
import com.bedrud.app.R
import com.bedrud.app.ui.components.BedrudSheetHandle
import com.bedrud.app.ui.theme.BedrudShapeTokens
import com.bedrud.app.ui.theme.Dimens
import kotlinx.coroutines.launch

/**
 * The in-call chat, as a sheet over the call rather than a screen in front of it.
 *
 * It used to fill the window: the same background as the call, its own top app bar, and a plain fade
 * in. Nothing of the room was left on screen, so chat read as somewhere you went rather than
 * something the call has. As a sheet the tiles stay visible above it behind the scrim, and the reader
 * can still see who is talking while they type.
 *
 * Three heights, which the platform sheet already arbitrates: half, full, and gone. Dragging down
 * from full returns to half, and again dismisses. The keyboard takes it to full on its own, since a
 * half sheet with a keyboard over it leaves almost no conversation showing.
 *
 * The content is sized to the sheet's **visible** height rather than to the whole window. A bottom
 * sheet lays its content out from its top edge downwards, so a full-height column would push the
 * input dock below the screen whenever the sheet rested at half — and the dock has to sit on the
 * bottom edge at every height, exactly where the call's own controls bar is.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetingChatSheet(
    messages: List<ChatMessage>,
    input: String,
    currentIdentity: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onSendAttachment: (String, ChatAttachment) -> Unit,
    onSendPoll: (ChatPoll) -> Unit,
    onToggleReaction: (String, String) -> Unit,
    onVote: (String, String) -> Unit,
    resolveName: (String) -> String,
    onClose: () -> Unit,
    imageContext: ChatImageContext?,
    @StringRes sendDisabledReason: Int?,
    onVisibleChange: (Boolean) -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    // The window as the sheet measures it, in the same pixels its offset is reported in. The
    // configuration's screenHeightDp leaves the system bars out and overshot the content by exactly
    // that much, which pushed the input dock off the bottom of the screen.
    val windowHeightPx = LocalWindowInfo.current.containerSize.height.toFloat()

    // Reported from the sheet's *target*, not from whether it is still on screen: a swipe down
    // settles the sheet over a few hundred milliseconds, and `onDismissRequest` only arrives at the
    // end of that. The chat button in the controls bar was left looking active the whole way down.
    LaunchedEffect(sheetState.targetValue) {
        onVisibleChange(sheetState.targetValue != SheetValue.Hidden)
    }

    // A keyboard over a half-open sheet leaves a sliver of conversation, so typing takes it to full.
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    LaunchedEffect(imeVisible) {
        if (imeVisible) sheetState.expand()
    }

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        shape = BedrudShapeTokens.sheetTop,
        containerColor = BottomSheetDefaults.ContainerColor,
        // The sheet consumes the navigation-bar inset by default, which left the composer's own
        // padding resolving to nothing and the gesture bar drawing across it. The content applies
        // the inset itself, together with the keyboard's.
        contentWindowInsets = { WindowInsets(0) },
        // The handle is drawn inside the content rather than in the sheet's own slot: the slot sits
        // above the content and its height is not something the content can subtract, so a content
        // sized to the visible height overflowed by exactly one handle and took the dock with it.
        dragHandle = null,
    ) {
        // The content keeps its full height and the part hanging below the screen is padded away,
        // rather than the content being sized to what is visible. Sizing it that way fed the sheet's
        // own height back into the offset that produced it, and the sheet collapsed into a sliver.
        BoxWithConstraints {
            // Fully expanded still stops short of the top, so the room name and a band of the call
            // stay in view. A sheet that reaches the status bar is a screen again.
            val topGapPx = WindowInsets.statusBars.getTop(density) +
                with(density) { GapAboveSheet.toPx() }
            val contentPx = (with(density) { maxHeight.toPx() } - topGapPx).coerceAtLeast(0f)

            // Read inside composition, so a drag moves the dock frame by frame. Before the first
            // layout there is no offset yet, and resting half-open is the honest guess.
            val offset = runCatching { sheetState.requireOffset() }
                .getOrNull()
                ?: (windowHeightPx * (1f - HalfOpenFraction))
            val hiddenBelowScreen = (contentPx - (windowHeightPx - offset)).coerceIn(0f, contentPx)

            Column(
                modifier = Modifier
                    .height(with(density) { contentPx.toDp() })
                    .padding(bottom = with(density) { hiddenBelowScreen.toDp() })
            ) {
                BedrudSheetHandle(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    onClickLabel = stringResource(R.string.meeting_panel_chat),
                    onClick = {
                        scope.launch {
                            if (sheetState.currentValue == SheetValue.Expanded) {
                                sheetState.partialExpand()
                            } else {
                                sheetState.expand()
                            }
                        }
                    },
                )
                MeetingChatPanel(
                    messages = messages,
                    input = input,
                    currentIdentity = currentIdentity,
                    onInputChange = onInputChange,
                    onSend = onSend,
                    onSendAttachment = onSendAttachment,
                    onSendPoll = onSendPoll,
                    onToggleReaction = onToggleReaction,
                    onVote = onVote,
                    resolveName = resolveName,
                    imageContext = imageContext,
                    sendDisabledReason = sendDisabledReason,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Where the sheet rests when it opens: half the window, so the call above it is still worth seeing. */
private const val HalfOpenFraction = 0.5f

/** Kept clear above a fully expanded sheet, on top of the status bar, so the call is never gone. */
private val GapAboveSheet = Dimens.space12

