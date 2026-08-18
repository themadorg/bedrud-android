package com.bedrud.app.ui.screens.meeting

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextDirection
import com.bedrud.app.R
import com.bedrud.app.core.BidiUtils
import com.bedrud.app.core.meeting.chat.ChatPoll
import com.bedrud.app.core.meeting.chat.results
import com.bedrud.app.core.meeting.chat.totalVotes
import com.bedrud.app.core.meeting.chat.voteOf
import com.bedrud.app.ui.components.BedrudBottomSheet
import com.bedrud.app.ui.components.BedrudSheetTitle
import com.bedrud.app.ui.theme.BedrudShapeTokens
import com.bedrud.app.ui.theme.Dimens

/**
 * A poll in the conversation, drawn as the message it arrived in.
 *
 * The tally is on show from the first vote rather than kept until this reader has voted: everybody
 * in the call can see the room anyway, so hiding the count buys no secrecy and only makes the poll
 * useless to whoever is still deciding.
 *
 * Tapping an answer casts a vote, or moves one already cast. Nothing takes a vote back — the wire
 * has no packet that says so, and a control that only worked on this device would be a lie.
 *
 * A poll keeps the neutral bubble colour even when this reader started it, where a message would
 * take the sender's own. The tally belongs to the room rather than to whoever asked, and the
 * accented container turned every unfilled answer into a black slab on crimson. Which side it sits
 * on still says whose it is.
 */
@Composable
fun ChatPollBubble(
    poll: ChatPoll,
    currentIdentity: String,
    shape: RoundedCornerShape,
    /** Null when this reader may not vote — the poll still shows, and the answers stop answering. */
    onVote: ((String) -> Unit)?,
    onShowResults: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val myVote = poll.voteOf(currentIdentity)
    val total = poll.totalVotes

    Column(
        modifier = modifier
            .width(Dimens.chatPollWidth)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, shape)
            // Measured from a capture rather than matched as numbers: a question's line box adds
            // ~2dp of leading above its caps while the footer's label leaves ~5dp under its
            // baseline, so equal padding put the title 10dp from the edge against 13dp at the sides.
            // Top 12 / bottom 8 lands the *ink* at roughly 14 / 13 / 13, which reads even with a
            // touch more air above the heading.
            .padding(
                start = Dimens.space12,
                end = Dimens.space12,
                top = Dimens.space12,
                bottom = Dimens.space8,
            ),
        verticalArrangement = Arrangement.spacedBy(Dimens.space6),
    ) {
        Text(
            text = BidiUtils.wrap(poll.question),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
                textDirection = BidiUtils.textDirection(poll.question),
                // Balanced, so a two-line question splits evenly instead of filling line one and
                // leaving a stub: "Do we run the numbers now, or / after the demo?" read as a
                // dangling conjunction above half an empty line.
                lineBreak = LineBreak.Heading,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )

        poll.results().forEach { result ->
            PollAnswer(
                text = result.option.text,
                percent = result.percent,
                showShare = total > 0,
                isMine = myVote == result.option.id,
                onClick = onVote?.let { vote -> { vote(result.option.id) } },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = pluralStringResource(R.plurals.meeting_chat_poll_voteCount, total, total),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.meeting_chat_poll_viewResults),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(BedrudShapeTokens.chip)
                    .clickable(onClick = onShowResults)
                    .padding(horizontal = Dimens.space4, vertical = Dimens.space2),
            )
        }
    }
}

/**
 * One answer: a tap target, a bar showing its share, and a tick when it is this reader's own vote.
 *
 * The bar is drawn behind the label rather than beside it, so an answer keeps the full width for
 * its text however the votes fall.
 */
@Composable
private fun PollAnswer(
    text: String,
    percent: Int,
    showShare: Boolean,
    isMine: Boolean,
    onClick: (() -> Unit)?,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(Dimens.chatPollOption)
            .clip(BedrudShapeTokens.chip)
            // A shade *lighter* than the bubble, never darker. The track was the panel's own surface
            // back when the bubble could be the accent colour; against the neutral bubble that reads
            // as a hole cut in the card rather than as a row waiting to be tapped.
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .let { base -> if (onClick == null) base else base.clickable(onClick = onClick) },
    ) {
        if (showShare && percent > 0) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(percent / PercentPerWhole)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = PollBarAlpha)),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Dimens.space8),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.space4),
            ) {
                if (isMine) {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(Dimens.iconXs),
                    )
                }
                Text(
                    text = BidiUtils.wrap(text),
                    style = MaterialTheme.typography.bodySmall.copy(
                        textDirection = BidiUtils.textDirection(text),
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (showShare) {
                Text(
                    text = "$percent$PercentSign",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Who voted for what, for when the bars are not the question being asked.
 *
 * Names come from the room, so somebody who has since left shows as whatever the call knew them by
 * — the wire carries an identity and nothing else, and inventing a name for it would be worse.
 */
@Composable
fun ChatPollResultsSheet(
    poll: ChatPoll,
    currentIdentity: String,
    resolveName: (String) -> String,
    onDismiss: () -> Unit,
) {
    val youLabel = stringResource(R.string.meeting_label_you)

    BedrudBottomSheet(onDismiss = onDismiss) {
        BedrudSheetTitle(text = stringResource(R.string.meeting_chat_poll_results))

        Text(
            text = BidiUtils.wrap(poll.question),
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.SemiBold,
                textDirection = BidiUtils.textDirection(poll.question),
                lineBreak = LineBreak.Heading,
            ),
            modifier = Modifier.padding(bottom = Dimens.space8),
        )

        poll.results().forEach { result ->
            Column(modifier = Modifier.padding(bottom = Dimens.space12)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = BidiUtils.wrap(result.option.text),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            textDirection = BidiUtils.textDirection(result.option.text),
                        ),
                    )
                    Text(
                        text = pluralStringResource(
                            R.plurals.meeting_chat_poll_voteCount,
                            result.count,
                            result.count,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = if (result.voters.isEmpty()) {
                        stringResource(R.string.meeting_chat_poll_noVotes)
                    } else {
                        // Each name is isolated before the commas go in: a Persian name beside an
                        // English one otherwise drags the separator to the wrong end of it.
                        result.voters.joinToString(VoterSeparator) { identity ->
                            BidiUtils.wrap(
                                if (identity == currentIdentity) youLabel else resolveName(identity)
                            )
                        }
                    },
                    style = MaterialTheme.typography.labelSmall.copy(
                        textDirection = TextDirection.Content,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** How much of an answer row the bar covers at 100%, since the share is counted in whole percent. */
private const val PercentPerWhole = 100f
private const val PercentSign = "%"

/** How far the winning bar tints its row: readable behind the label, not a block of colour. */
private const val PollBarAlpha = 0.35f

private const val VoterSeparator = ", "
