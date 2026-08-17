package com.bedrud.app.ui.screens.meeting

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.bedrud.app.R
import com.bedrud.app.core.meeting.chat.ChatReactions
import com.bedrud.app.core.meeting.chat.QuickReactions
import com.bedrud.app.core.meeting.chat.grouped
import com.bedrud.app.ui.theme.BedrudShapeTokens
import com.bedrud.app.ui.theme.Dimens

/**
 * The quick reactions, offered where the message is rather than in a panel of its own.
 *
 * A fixed set of eight, shared with the other clients, instead of the full emoji picker the web has:
 * a picker large enough to search would cover the conversation it is reacting to, and the eight
 * cover what a call actually needs — agreement, laughter, surprise, applause.
 */
@Composable
fun ChatReactionPicker(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    showReactions: Boolean = true,
    extraItems: (@Composable ColumnScope.() -> Unit)? = null,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (showReactions) {
            Row(modifier = Modifier.padding(horizontal = Dimens.space4)) {
                QuickReactions.forEach { emoji ->
                    Text(
                        text = emoji,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .clip(BedrudShapeTokens.pill)
                            .clickable {
                                onPick(emoji)
                                onDismiss()
                            }
                            .size(Dimens.chatReactionTarget)
                            .wrapContentSize(Alignment.Center),
                    )
                }
            }
        }
        extraItems?.invoke(this)
    }
}

/**
 * The reactions already on a message, one chip per emoji.
 *
 * Wraps rather than scrolls: a message with more reactions than fit on a line is unusual enough
 * that growing the row downwards is kinder than hiding half of them behind a gesture.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatReactionRow(
    reactions: ChatReactions,
    currentIdentity: String,
    /** Null when this reader may not react — the chips still show, they just do not answer back. */
    onToggle: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val chips = reactions.grouped(currentIdentity)
    if (chips.isEmpty()) return

    FlowRow(
        modifier = modifier.padding(top = Dimens.space2),
        horizontalArrangement = Arrangement.spacedBy(Dimens.space4),
        verticalArrangement = Arrangement.spacedBy(Dimens.space4),
    ) {
        chips.forEach { chip ->
            val description = stringResource(R.string.meeting_contentDescription_react)
            Row(
                modifier = Modifier
                    .heightIn(min = Dimens.chatReactionChip)
                    // Wider than it is tall, always: one emoji and no count came out square, and a
                    // pill radius on a square is a circle — which read as an avatar, not a chip.
                    .widthIn(min = Dimens.chatReactionChipMinWidth)
                    .clip(BedrudShapeTokens.pill)
                    // The reader's own reaction is filled the way their own messages are, so one
                    // glance answers "did I react to this" without counting.
                    .background(
                        if (chip.mine) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                    )
                    .let { base ->
                        if (onToggle == null) base else base.clickable { onToggle(chip.emoji) }
                    }
                    .semantics { contentDescription = description }
                    // Asymmetric on purpose: an emoji's ink starts inset inside its glyph box while a
                    // digit almost fills its advance width, so equal padding leaves the emoji side
                    // looking loose and the count side tight. Trimmed to read even.
                    .padding(
                        start = Dimens.space6,
                        end = if (chip.count > 1) Dimens.space8 else Dimens.space6,
                        top = Dimens.space2,
                        bottom = Dimens.space2,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(
                    space = Dimens.space2,
                    alignment = Alignment.CenterHorizontally,
                ),
            ) {
                Text(text = chip.emoji, style = MaterialTheme.typography.labelMedium)
                // One reaction needs no tally: the emoji is already the whole message, and "1"
                // beside it only asks the reader to count to one.
                if (chip.count > 1) {
                    Text(
                        text = chip.count.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (chip.mine) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}
