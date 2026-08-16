package com.bedrud.app.ui.screens.meeting

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
    serverURL: String,
    accessToken: String?,
    onImageClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = BedrudShapeTokens.chatBubble(
        isLocal = row.isLocal,
        tuckedAbove = !row.startsRun,
        tuckedBelow = !row.endsRun,
    )
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
            ChatBubble(
                row = row,
                shape = shape,
                serverURL = serverURL,
                accessToken = accessToken,
                onImageClick = onImageClick,
            )
        }
    }
}

@Composable
private fun ChatBubble(
    row: ChatRow,
    shape: RoundedCornerShape,
    serverURL: String,
    accessToken: String?,
    onImageClick: (String) -> Unit,
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
                )
            }
        if (message.text.isNotEmpty()) {
            // Selection is scoped to the one bubble rather than wrapping the whole list: the list is
            // reversed and recycles its items, so a drag that ran between messages would keep
            // losing the end it started from.
            SelectionContainer {
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
                        .padding(horizontal = Dimens.space12, vertical = Dimens.space8),
                )
            }
        }
    }
}

/** A picture in a message, capped so a tall photo cannot push the conversation off screen. */
@Composable
private fun ChatMessageImage(
    url: String,
    serverURL: String,
    accessToken: String?,
    shape: RoundedCornerShape,
    onClick: () -> Unit,
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
            .clickable(onClick = onClick),
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
