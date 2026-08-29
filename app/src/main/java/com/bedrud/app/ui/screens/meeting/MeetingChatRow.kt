package com.bedrud.app.ui.screens.meeting

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import com.bedrud.app.R
import com.bedrud.app.core.BidiUtils
import com.bedrud.app.core.meeting.chat.ChatRow
import com.bedrud.app.core.meeting.chat.ChatWire
import com.bedrud.app.core.meeting.chat.grouped
import com.bedrud.app.ui.components.ChatImage
import com.bedrud.app.ui.components.InitialsAvatar
import com.bedrud.app.ui.theme.BedrudShapeTokens
import com.bedrud.app.ui.theme.Dimens
import com.bedrud.app.ui.theme.rememberInkCenteringOffset
import com.bedrud.app.ui.util.setPlainText
import kotlinx.coroutines.launch

/**
 * One message, drawn as part of its sender's run.
 *
 * The avatar and the name appear only on the message that opens a run, and the corners facing the
 * sender's own side tighten wherever another of their messages sits against them — which is what
 * makes several messages read as one turn rather than a stack of separate cards. Continuations are
 * indented past where the avatar would be so every bubble in a run shares an edge.
 *
 * The local side gets neither avatar nor name: the messages are already on the reader's own side of
 * the panel, and repeating "you" above them says nothing.
 */
@Composable
fun MeetingChatRow(
    row: ChatRow,
    currentIdentity: String,
    canParticipate: Boolean,
    serverURL: String,
    accessToken: String?,
    onImageClick: (String) -> Unit,
    onToggleReaction: (String, String) -> Unit,
    onVote: (String, String) -> Unit,
    onShowPollResults: (String) -> Unit,
    onShowReactions: (String) -> Unit,
    onLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = BedrudShapeTokens.chatBubble(
        isLocal = row.isLocal,
        tuckedAbove = !row.startsRun,
        tuckedBelow = !row.endsRun,
    )
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = if (row.startsRun) Dimens.chatClusterGap else Dimens.chatBubbleGap),
        horizontalArrangement = if (row.isLocal) Arrangement.End else Arrangement.Start,
    ) {
        if (!row.isLocal) {
            if (row.startsRun) {
                InitialsAvatar(
                    name = row.senderName,
                    size = Dimens.chatAvatar,
                    textStyle = MaterialTheme.typography.labelSmall,
                    containerColor = avatarColorFor(row.senderIdentity.ifBlank { row.senderName }),
                    fallbackInitial = "",
                )
            } else {
                Spacer(modifier = Modifier.width(Dimens.chatAvatar))
            }
            Spacer(modifier = Modifier.width(Dimens.space8))
        }
        Column(horizontalAlignment = if (row.isLocal) Alignment.End else Alignment.Start) {
            if (row.startsRun && !row.isLocal) {
                Text(
                    text = row.senderName,
                    // Content direction, not the interface's: a Persian name in an English call
                    // still reads right to left.
                    style = MaterialTheme.typography.labelSmall.copy(
                        textDirection = TextDirection.Content,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Dimens.space2),
                )
            }
            // The menu is anchored inside this box so it opens against the bubble it belongs to,
            // wherever that bubble has scrolled to.
            Box {
                ChatBubble(
                    row = row,
                    shape = shape,
                    currentIdentity = currentIdentity,
                    canParticipate = canParticipate,
                    serverURL = serverURL,
                    accessToken = accessToken,
                    onImageClick = onImageClick,
                    onLongPress = { menuOpen = true },
                    onToggleReaction = { emoji -> onToggleReaction(row.message.id, emoji) },
                    onVote = onVote,
                    onShowPollResults = onShowPollResults,
                    onLinkClick = onLinkClick,
                )
                ChatMessageMenu(
                    expanded = menuOpen,
                    text = row.message.text,
                    // Blocked from chatting is blocked from reacting — the room would drop the
                    // packet anyway, and offering the pill would be a control that quietly does
                    // nothing.
                    canReact = canParticipate,
                    isLocal = row.isLocal,
                    reactions = row.message.reactions,
                    onDismiss = { menuOpen = false },
                    onReact = { emoji -> onToggleReaction(row.message.id, emoji) },
                    onShowReactions = { onShowReactions(row.message.id) },
                )
            }
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatBubble(
    row: ChatRow,
    shape: RoundedCornerShape,
    currentIdentity: String,
    canParticipate: Boolean,
    serverURL: String,
    accessToken: String?,
    onImageClick: (String) -> Unit,
    onLongPress: () -> Unit,
    onToggleReaction: (String) -> Unit,
    onVote: (String, String) -> Unit,
    onShowPollResults: (String) -> Unit,
    onLinkClick: (String) -> Unit,
) {
    val message = row.message
    val images = message.attachments.filter { it.kind == ChatWire.ATTACHMENT_KIND_IMAGE }
    val bubbleColor = if (row.isLocal) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val bubbleContentColor = if (row.isLocal) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    // The chips belong to the last container the message has — its text bubble, else its poll, else
    // its picture — so one conversation carries one placement however a message is made up. They
    // stay on show whether or not this reader may react: they are part of what was said, and a
    // block takes away the reply, not the record.
    //
    // A message with nothing to show hosts nothing: the chip row draws no space when it is empty,
    // but the container it would sit in still would, and an unreacted picture came out wearing an
    // 8dp band of bubble along its bottom edge.
    val chipsOn = when {
        message.reactions.grouped(currentIdentity).isEmpty() -> ChipHost.NONE
        message.text.isNotEmpty() -> ChipHost.TEXT
        message.poll != null -> ChipHost.POLL
        images.isNotEmpty() -> ChipHost.IMAGE
        else -> ChipHost.NONE
    }
    val reactionRow: @Composable (Color) -> Unit = { contentColor ->
        ChatReactionRow(
            reactions = message.reactions,
            currentIdentity = currentIdentity,
            onToggle = if (canParticipate) onToggleReaction else null,
            contentColor = contentColor,
            modifier = Modifier.padding(top = Dimens.space4),
        )
    }

    Column(
        horizontalAlignment = if (row.isLocal) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(Dimens.chatBubbleGap),
    ) {
        images.forEachIndexed { index, attachment ->
            // Neutral on both sides, unlike every other bubble. The accent tint says "mine" by
            // colouring the thing that was said, and behind a line of text that is what it does —
            // but a picture is its own body, so the tint colours nothing and only competes with the
            // photo. Measured on a full-width photo in dark, where `primaryContainer` came out as a
            // saturated band along the bottom edge; the same colour behind text never reads that
            // heavy because it never runs that wide.
            ChatMessageImage(
                url = attachment.url,
                serverURL = serverURL,
                accessToken = accessToken,
                shape = shape,
                bubbleColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                onClick = { onImageClick(attachment.url) },
                onLongPress = onLongPress,
                reactions = if (chipsOn == ChipHost.IMAGE && index == images.lastIndex) {
                    { reactionRow(MaterialTheme.colorScheme.onSurface) }
                } else {
                    null
                },
            )
        }
        message.poll?.let { poll ->
            ChatPollBubble(
                poll = poll,
                currentIdentity = currentIdentity,
                shape = shape,
                onVote = if (canParticipate) {
                    { optionId -> onVote(message.id, optionId) }
                } else {
                    null
                },
                onShowResults = { onShowPollResults(message.id) },
                reactions = if (chipsOn == ChipHost.POLL) {
                    { reactionRow(MaterialTheme.colorScheme.onSurface) }
                } else {
                    null
                },
                modifier = Modifier.combinedClickable(
                    onClick = {},
                    onLongClick = onLongPress,
                ),
            )
        }
        if (message.text.isNotEmpty()) {
            val contentColor = bubbleContentColor
            val bodyStyle = MaterialTheme.typography.bodyMedium.copy(
                textDirection = BidiUtils.textDirection(message.text),
            )
            Column(
                modifier = Modifier
                    .background(bubbleColor, shape)
                    .clip(shape)
                    .combinedClickable(onClick = {}, onLongClick = onLongPress)
                    .padding(horizontal = Dimens.space12, vertical = Dimens.space8),
            ) {
                Text(
                    text = rememberChatMessageText(
                        text = message.text,
                        contentColor = contentColor,
                        onLinkClick = onLinkClick,
                    ),
                    style = bodyStyle,
                    color = contentColor,
                    // The bubble keeps its height from the font's box, which is what leaves room
                    // for every script it may carry — Persian tails and Arabic marks included.
                    // Only the asymmetry is corrected, by moving the line to where its letters
                    // centre rather than where the box does. Spacing from the baselines instead
                    // was tried and rejected: it ties the bubble's height to cap height, a Latin
                    // measure, and left Persian descenders close to the edge.
                    modifier = Modifier.offset(y = rememberInkCenteringOffset(message.text, bodyStyle)),
                )
                // Start-aligned under the text rather than tucked against the bubble's outer edge,
                // so a reaction on a long message begins where its first line does.
                reactionRow(contentColor)
            }
        }
    }
}

/**
 * A picture in a message, capped so a tall photo cannot push the conversation off screen.
 *
 * With [reactions] the picture is a bubble like any other message: the chips sit on the bubble's own
 * surface along its bottom edge, which is what lets them keep the colours they use everywhere else.
 * Drawn over the photo instead they would need a scrim and a second, opaque chip colour — a
 * reaction the reader has not joined is a 16% wash of the surrounding text colour, legible only
 * because there is normally an opaque bubble behind it.
 *
 * Without reactions nothing is drawn around the picture at all, so an unreacted photo is exactly
 * what it was: the container has no padding, and the photo covers every edge of it but the bottom.
 * That is also why the photo needs no inset radius — it shares the container's corners rather than
 * sitting inside them.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatMessageImage(
    url: String,
    serverURL: String,
    accessToken: String?,
    shape: RoundedCornerShape,
    bubbleColor: Color,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    reactions: (@Composable () -> Unit)?,
) {
    val imageShape = if (reactions == null) {
        shape
    } else {
        shape.copy(bottomStart = ZeroCornerSize, bottomEnd = ZeroCornerSize)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth(ChatImageWidthFraction)
            .then(if (reactions == null) Modifier else Modifier.background(bubbleColor, shape))
            .clip(shape),
    ) {
        ChatImage(
            url = url,
            serverURL = serverURL,
            accessToken = accessToken,
            contentDescription = stringResource(R.string.meeting_contentDescription_viewImage),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = Dimens.chatImageMaxHeight)
                .clip(imageShape)
                .combinedClickable(onClick = onClick, onLongClick = onLongPress),
            contentScale = ContentScale.Crop,
        )
        if (reactions != null) {
            Column(
                modifier = Modifier.padding(
                    start = Dimens.space12,
                    end = Dimens.space12,
                    bottom = Dimens.space8,
                ),
            ) {
                reactions()
            }
        }
    }
}

/** Which of a message's containers carries its reaction chips. */
private enum class ChipHost { TEXT, POLL, IMAGE, NONE }

/**
 * A stable accent for one person, so a busy room stays scannable by colour as well as by name.
 *
 * Drawn from the theme's own accent roles rather than a palette of its own, so it stays on-brand
 * and keeps working in both light and dark.
 */
@Composable
private fun avatarColorFor(key: String): Color {
    val accents = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.secondary,
    )
    // String.hashCode is stable across runs and devices, unlike the identity hash. Int.mod never
    // returns a negative, which the remainder operator would for half the possible hashes.
    return accents[key.hashCode().mod(accents.size)]
}

/** How much of the panel's width a picture may take, leaving the column's edge readable. */
private const val ChatImageWidthFraction = 0.8f
