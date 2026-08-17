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
                    canVote = canParticipate,
                    serverURL = serverURL,
                    accessToken = accessToken,
                    onImageClick = onImageClick,
                    onLongPress = { menuOpen = true },
                    onVote = onVote,
                    onShowPollResults = onShowPollResults,
                )
                ChatMessageMenu(
                    expanded = menuOpen,
                    text = row.message.text,
                    canReact = canParticipate,
                    onDismiss = { menuOpen = false },
                    onReact = { emoji -> onToggleReaction(row.message.id, emoji) },
                )
            }
            // The chips stay on show whether or not this reader may react: they are part of what
            // was said, and a block takes away the reply, not the record.
            ChatReactionRow(
                reactions = row.message.reactions,
                currentIdentity = currentIdentity,
                onToggle = if (canParticipate) {
                    { emoji -> onToggleReaction(row.message.id, emoji) }
                } else {
                    null
                },
            )
        }
    }
}

/**
 * What a long press on a message offers: the quick reactions, and copying the text.
 *
 * A long press used to start selecting text inside the bubble. Both cannot own the gesture, and on
 * a phone the message menu is what a long press means everywhere else — so selection became this
 * menu's copy action, which is what the selecting was for.
 */
@Composable
private fun ChatMessageMenu(
    expanded: Boolean,
    text: String,
    canReact: Boolean,
    onDismiss: () -> Unit,
    onReact: (String) -> Unit,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val clipLabel = stringResource(R.string.app_name)

    ChatReactionPicker(
        expanded = expanded,
        // Blocked from chatting is blocked from reacting — the room would drop the packet anyway,
        // and offering the row would be a control that quietly does nothing.
        showReactions = canReact,
        onDismiss = onDismiss,
        onPick = onReact,
        // A message with only a picture in it has nothing to copy, so the row of reactions is the
        // whole menu.
        extraItems = if (text.isEmpty()) {
            null
        } else {
            {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.common_action_copy)) },
                    leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
                    onClick = {
                        scope.launch { clipboard.setPlainText(clipLabel, text) }
                        onDismiss()
                    },
                )
            }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatBubble(
    row: ChatRow,
    shape: RoundedCornerShape,
    currentIdentity: String,
    canVote: Boolean,
    serverURL: String,
    accessToken: String?,
    onImageClick: (String) -> Unit,
    onLongPress: () -> Unit,
    onVote: (String, String) -> Unit,
    onShowPollResults: (String) -> Unit,
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
                onVote = if (canVote) {
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
            Text(
                text = BidiUtils.wrap(message.text),
                style = MaterialTheme.typography.bodyMedium.copy(
                    textDirection = BidiUtils.textDirection(message.text),
                ),
                color = if (row.isLocal) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier
                    .background(
                        if (row.isLocal) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        shape,
                    )
                    .clip(shape)
                    .combinedClickable(onClick = {}, onLongClick = onLongPress)
                    .padding(horizontal = Dimens.space12, vertical = Dimens.space8),
            )
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
