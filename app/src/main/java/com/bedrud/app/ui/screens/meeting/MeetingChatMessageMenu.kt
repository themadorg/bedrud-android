package com.bedrud.app.ui.screens.meeting

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.bedrud.app.R
import com.bedrud.app.core.meeting.chat.ChatReactions
import com.bedrud.app.core.meeting.chat.QuickReactions
import com.bedrud.app.ui.theme.BedrudShapeTokens
import com.bedrud.app.ui.theme.Dimens
import com.bedrud.app.ui.theme.Elevation
import com.bedrud.app.ui.util.setPlainText
import com.bedrud.app.ui.util.sharePlainText
import kotlinx.coroutines.launch

/**
 * What a long press on a message offers: the reactions, and what can be done with the message.
 *
 * Two surfaces rather than one, straddling the bubble — reactions above it, actions below — so the
 * message being acted on stays readable between them. A single menu covered the very thing it was
 * about, which is the wrong way round for a menu whose first row is a reply to it.
 *
 * The bubble does not move to make room. Lifting it clear the way the messengers do needs the whole
 * conversation dimmed behind an overlay, and a menu that jumps the list on long press is worse than
 * one that opens quietly where the message already is.
 *
 * A long press used to start selecting text inside the bubble. Both cannot own the gesture, and on a
 * phone the message menu is what a long press means everywhere else — so selection became this
 * menu's copy action, which is what the selecting was for.
 */
@Composable
fun ChatMessageMenu(
    expanded: Boolean,
    text: String,
    canReact: Boolean,
    isLocal: Boolean,
    reactions: ChatReactions,
    onDismiss: () -> Unit,
    onReact: (String) -> Unit,
    onShowReactions: () -> Unit,
) {
    if (!expanded) return

    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val clipLabel = stringResource(R.string.app_name)
    val context = LocalContext.current
    val shareChooserTitle = stringResource(R.string.meeting_chat_shareChooser)

    // Both surfaces hang off the bubble's own outer edge, the side the message is already on, so the
    // menu reads as belonging to it rather than floating loose in the panel.
    val alignToEnd = isLocal
    val gap = with(LocalDensity.current) { Dimens.chatMenuGap.roundToPx() }

    // Where the actions card settled, so the pill can sit above whichever is higher — the bubble, or
    // the card when it had to flip over the top of it. The last message in a panel has the composer
    // right beneath it and no room below, which is the case this exists for.
    val cardTop = remember { mutableIntStateOf(Int.MAX_VALUE) }

    Popup(
        popupPositionProvider = ChatActionsPosition(gap, alignToEnd, cardTop),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        ChatMessageActions(
            text = text,
            reactions = reactions,
            onCopy = {
                scope.launch { clipboard.setPlainText(clipLabel, text) }
                onDismiss()
            },
            onShare = {
                context.sharePlainText(text, shareChooserTitle)
                onDismiss()
            },
            onShowReactions = {
                onShowReactions()
                onDismiss()
            },
        )
    }

    if (canReact) {
        Popup(
            popupPositionProvider = ChatPillPosition(gap, alignToEnd, cardTop),
            // Not focusable: the actions card owns dismissal, and two focusable popups over one
            // anchor take it in turns to steal the back press from each other.
            properties = PopupProperties(focusable = false),
        ) {
            ChatReactionPill(onPick = { emoji ->
                onReact(emoji)
                onDismiss()
            })
        }
    }
}

/**
 * The quick reactions, as one floating pill above the message.
 *
 * A fixed set of eight, shared with the other clients, instead of the full emoji picker the web has:
 * a picker large enough to search would cover the conversation it is reacting to, and the eight
 * cover what a call actually needs — agreement, laughter, surprise, applause.
 */
@Composable
private fun ChatReactionPill(onPick: (String) -> Unit) {
    // What the row may occupy before it starts scrolling: the window, less what the popup leaves
    // free at either side, and never more than the cap. Read from the window rather than assumed,
    // because the narrow case is the one that overflows — eight targets already want 320dp.
    val windowWidth = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.width.toDp()
    }
    val maxRowWidth = (windowWidth - Dimens.chatReactionPickerInset)
        .coerceAtMost(Dimens.chatReactionPickerMaxWidth)

    Surface(
        shape = BedrudShapeTokens.pill,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = Elevation.level2,
        shadowElevation = Elevation.level3,
    ) {
        Row(
            // widthIn caps without stretching, so a set that fits still lays out at its own width
            // and never scrolls — the pill keeps its shape, and the gesture only appears once there
            // is something behind the edge to reach.
            modifier = Modifier
                .widthIn(max = maxRowWidth)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Dimens.space4),
        ) {
            QuickReactions.forEach { emoji ->
                Text(
                    text = emoji,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .clip(BedrudShapeTokens.pill)
                        .clickable { onPick(emoji) }
                        .size(Dimens.chatReactionTarget)
                        .wrapContentSize(Alignment.Center),
                )
            }
        }
    }
}

/**
 * What can be done with the message, as a card below it.
 *
 * Short on purpose: it offers what the app can actually do, not a row per action the messengers
 * happen to have. The reactions row appears only once there are reactions to break down, so the
 * card never opens on a control that would show an empty list.
 */
@Composable
private fun ChatMessageActions(
    text: String,
    reactions: ChatReactions,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onShowReactions: () -> Unit,
) {
    val reactionCount = reactions.size
    // Copying and sharing both act on the words, so a message that is only a picture offers
    // neither — the wire carries the image as a URL the receiving app could not resolve anyway.
    val hasText = text.isNotEmpty()
    if (reactionCount == 0 && !hasText) return

    Surface(
        shape = BedrudShapeTokens.card,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = Elevation.level2,
        shadowElevation = Elevation.level3,
    ) {
        Column(
            modifier = Modifier.widthIn(
                min = Dimens.chatMenuMinWidth,
                max = Dimens.chatMenuMaxWidth,
            ),
        ) {
            if (reactionCount > 0) {
                ChatMessageAction(
                    label = pluralStringResource(
                        R.plurals.meeting_chat_reactionCount,
                        reactionCount,
                        reactionCount,
                    ),
                    icon = Icons.Rounded.EmojiEmotions,
                    onClick = onShowReactions,
                )
                if (hasText) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            if (hasText) {
                ChatMessageAction(
                    label = stringResource(R.string.common_action_copy),
                    icon = Icons.Rounded.ContentCopy,
                    onClick = onCopy,
                )
                ChatMessageAction(
                    label = stringResource(R.string.common_action_share),
                    icon = Icons.Rounded.Share,
                    onClick = onShare,
                )
            }
        }
    }
}

/** One row of the actions card: what it does on the reading side, what it is on the far side. */
@Composable
private fun ChatMessageAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = Dimens.space16, vertical = Dimens.space12),
        verticalAlignment = Alignment.CenterVertically,
        // The icon sits at the far end rather than in front of the label, so the labels start on one
        // line down the card and the eye reads the actions before it reads their symbols.
        horizontalArrangement = Arrangement.spacedBy(Dimens.space16, Alignment.Start),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(Dimens.chatMenuIcon),
        )
    }
}

/**
 * The side the menu hangs from — the message's own outer edge, which swaps with the layout
 * direction: a local message sits right in English and left in Persian, and the menu follows it.
 */
private fun alignedX(
    anchorBounds: IntRect,
    windowSize: IntSize,
    layoutDirection: LayoutDirection,
    contentWidth: Int,
    alignToEnd: Boolean,
): Int {
    val alignRight = if (layoutDirection == LayoutDirection.Ltr) alignToEnd else !alignToEnd
    val x = if (alignRight) anchorBounds.right - contentWidth else anchorBounds.left
    return x.coerceIn(0, (windowSize.width - contentWidth).coerceAtLeast(0))
}

/**
 * Places the actions card, below the message where there is room and above it where there is not.
 *
 * Anchored rather than centred: the popup is declared inside the bubble's own box, so [anchorBounds]
 * is that bubble, wherever it has scrolled to. The flip matters for the newest message, which sits
 * against the composer with nothing below it — clamping the card into the window instead would slide
 * it up over the very message it belongs to.
 *
 * It records where it landed in [cardTop] so the pill can clear it.
 */
private class ChatActionsPosition(
    private val gap: Int,
    private val alignToEnd: Boolean,
    private val cardTop: MutableIntState,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val below = anchorBounds.bottom + gap
        val y = if (below + popupContentSize.height <= windowSize.height) {
            below
        } else {
            anchorBounds.top - popupContentSize.height - gap
        }.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0))
        cardTop.intValue = y
        return IntOffset(
            alignedX(anchorBounds, windowSize, layoutDirection, popupContentSize.width, alignToEnd),
            y,
        )
    }
}

/**
 * Places the reactions pill above whichever came out higher, the message or the flipped card, so the
 * two surfaces never land on top of each other.
 */
private class ChatPillPosition(
    private val gap: Int,
    private val alignToEnd: Boolean,
    private val cardTop: MutableIntState,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val ceiling = minOf(anchorBounds.top, cardTop.intValue)
        val y = (ceiling - popupContentSize.height - gap)
            .coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0))
        return IntOffset(
            alignedX(anchorBounds, windowSize, layoutDirection, popupContentSize.width, alignToEnd),
            y,
        )
    }
}
