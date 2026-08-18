package com.bedrud.app.ui.screens.meeting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.bedrud.app.R
import com.bedrud.app.core.meeting.chat.ChatPoll
import com.bedrud.app.core.meeting.chat.MaxPollOptions
import com.bedrud.app.core.meeting.chat.MinPollOptions
import com.bedrud.app.core.meeting.chat.newPoll
import com.bedrud.app.ui.components.BedrudBottomSheet
import com.bedrud.app.ui.components.BedrudButton
import com.bedrud.app.ui.components.BedrudButtonVariant
import com.bedrud.app.ui.components.BedrudSheetTitle
import com.bedrud.app.ui.components.BedrudTextField
import com.bedrud.app.ui.theme.Dimens

/**
 * Writes a poll: a question, and the answers to choose between.
 *
 * The answers cannot be reordered here, unlike on the web. Dragging a row inside a sheet that itself
 * scrolls and sits over a keyboard fights every gesture around it, and an answer in the wrong place
 * is fixed by retyping two short lines.
 *
 * Send stays closed until what has been typed is actually a poll — a question, and [MinPollOptions]
 * answers that are not blank — rather than sending something the room could not vote on.
 */
@Composable
fun MeetingChatPollSheet(
    onDismiss: () -> Unit,
    onCreate: (ChatPoll) -> Unit,
) {
    var question by remember { mutableStateOf("") }
    val options = remember { mutableStateListOf("", "") }
    val poll = newPoll(question, options.toList())

    BedrudBottomSheet(onDismiss = onDismiss) {
        BedrudSheetTitle(text = stringResource(R.string.meeting_chat_poll_new))

        BedrudTextField(
            value = question,
            onValueChange = { question = it },
            label = stringResource(R.string.meeting_chat_poll_question),
            placeholder = stringResource(R.string.meeting_chat_poll_questionPlaceholder),
            textStyle = MaterialTheme.typography.bodyMedium,
        )

        options.forEachIndexed { index, option ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.space4),
            ) {
                BedrudTextField(
                    value = option,
                    onValueChange = { options[index] = it },
                    label = stringResource(R.string.meeting_chat_poll_option, index + 1),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                // The last two answers keep their remove button drawn but disabled, so the row of
                // fields does not change width as answers come and go.
                IconButton(
                    onClick = { options.removeAt(index) },
                    enabled = options.size > MinPollOptions,
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.meeting_chat_poll_removeOption),
                        modifier = Modifier.size(Dimens.iconSm),
                    )
                }
            }
        }

        BedrudButton(
            text = stringResource(R.string.meeting_chat_poll_addOption),
            variant = BedrudButtonVariant.GHOST,
            enabled = options.size < MaxPollOptions,
            onClick = { options.add("") },
            leadingIcon = {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = null,
                    modifier = Modifier.size(Dimens.iconSm),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )

        BedrudButton(
            text = stringResource(R.string.meeting_chat_poll_send),
            enabled = poll != null,
            onClick = { poll?.let(onCreate) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Dimens.space4),
        )
    }
}
