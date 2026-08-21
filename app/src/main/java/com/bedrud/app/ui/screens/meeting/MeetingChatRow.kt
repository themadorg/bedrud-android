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
            // A message with text keeps its chips inside the bubble, along the bottom edge. One
            // that is only a picture or a poll has no text container to put them in, so they hang
            // below it as before.
            // TODO(#135): overlay them on the picture and put them inside the poll bubble, so one
            //  conversation does not carry two placements.
            //
            // Either way the chips stay on show whether or not this reader may react: they are part
            // of what was said, and a block takes away the reply, not the record.
            if (row.message.text.isEmpty()) {
                ChatReactionRow(
                    reactions = row.message.reactions,
                    currentIdentity = currentIdentity,
                    onToggle = if (canParticipate) {
                        { emoji -> onToggleReaction(row.message.id, emoji) }
                    } else {
                        null
                    },
                    // No bubble behind these, so they key off the panel itself.
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = Dimens.space2),
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
    Column(
        horizontalAlignment = if (row.isLocal) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(Dimens.chatBubbleGap),
    ) {
        message.attachments
            .filter { it.kind == ChatWire.ATTACHMENT_KIND_IMAGE }
            .forEach { attachment ->
                ChatMessageImage(
                    url = attachment.url,
                    serverURL = serverURL,
                    accessToken = accessToken,
                    shape = shape,
                    onClick = { onImageClick(attachment.url) },
                    onLongPress = onLongPress,
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
                modifier = Modifier.combinedClickable(
                    onClick = {},
                    onLongClick = onLongPress,
                ),
            )
        }
        if (message.text.isNotEmpty()) {
            val contentColor = if (row.isLocal) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            }
            val bodyStyle = MaterialTheme.typography.bodyMedium.copy(
                textDirection = BidiUtils.textDirection(message.text),
            )
            Column(
                modifier = Modifier
                    .background(
                        if (row.isLocal) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            // Two full container steps above the conversation's
                            // `surfaceContainerLow` ground. One step was tried and is not enough:
                            // the warm neutral ramp is compressed (11 RGB units in light), and a
                            // bubble one step off its ground read as the ground.
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                        shape,
                    )
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
                ChatReactionRow(
                    reactions = message.reactions,
                    currentIdentity = currentIdentity,
                    onToggle = if (canParticipate) onToggleReaction else null,
                    contentColor = contentColor,
                    modifier = Modifier.padding(top = Dimens.space4),
                )
            }
        }
    }
}

/** A picture in a message, capped so a tall photo cannot push the conversation off screen. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatMessageImage(
    url: String,
    serverURL: String,
    accessToken: String?,
    shape: RoundedCornerShape,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    ChatImage(
        url = url,
        serverURL = serverURL,
        accessToken = accessToken,
        contentDescription = stringResource(R.string.meeting_contentDescription_viewImage),
        modifier = Modifier
            .fillMaxWidth(ChatImageWidthFraction)
            .heightIn(max = Dimens.chatImageMaxHeight)
            .clip(shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
        contentScale = ContentScale.Crop,
    )
}

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
