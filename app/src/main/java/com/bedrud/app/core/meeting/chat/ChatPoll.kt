package com.bedrud.app.core.meeting.chat

import java.util.UUID
import kotlin.math.roundToInt

/** One answer a poll offers. Ids travel on the wire; the text is only ever read by a person. */
data class ChatPollOption(
    val id: String,
    val text: String,
)

/**
 * A poll, carried inside the chat message that created it.
 *
 * Votes are keyed by the voter's identity, so one person holds one vote and voting again replaces
 * what they chose before. There is no way to take a vote back: the wire has no packet for it, and
 * the other clients in a room would not know what to do with one.
 *
 * Nobody owns the tally. Every client applies the votes it hears and arrives at the same numbers,
 * which also means a vote cast before this device joined is a vote it will never see.
 */
data class ChatPoll(
    val id: String,
    val question: String,
    val options: List<ChatPollOption>,
    val votes: Map<String, String> = emptyMap(),
)

/**
 * What the composer will build. Two answers is the least that makes a question; six is where the
 * bubble stops being readable at a glance. The other clients compose within the same bounds.
 *
 * Received polls are not held to this — a client that offers more is still showing its room a real
 * poll, and refusing to draw it would help nobody.
 */
const val MinPollOptions = 2
const val MaxPollOptions = 6

/**
 * Builds a poll from what somebody typed, minting the ids the wire needs.
 *
 * Blank answers are dropped rather than sent as empty rows — leaving a half-filled composer is how
 * people work, and an answer nobody wrote is not an answer. Null when what is left is not a poll at
 * all: no question, or fewer than [MinPollOptions] answers to choose between.
 */
fun newPoll(question: String, optionTexts: List<String>): ChatPoll? {
    val trimmedQuestion = question.trim()
    val options = optionTexts
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { ChatPollOption(id = UUID.randomUUID().toString(), text = it) }
    if (trimmedQuestion.isEmpty() || options.size < MinPollOptions) return null
    return ChatPoll(id = UUID.randomUUID().toString(), question = trimmedQuestion, options = options)
}

val ChatPoll.totalVotes: Int get() = votes.size

/** What this person voted for, or null while they have not voted. */
fun ChatPoll.voteOf(voterIdentity: String): String? = votes[voterIdentity]

/**
 * Records one vote, replacing whatever that voter chose before.
 *
 * A vote for an option the poll does not have changes nothing: it can only come from a client
 * working off a different version of the poll, and guessing which option was meant is worse than
 * ignoring it.
 */
fun ChatPoll.withVote(voterIdentity: String, optionId: String): ChatPoll {
    if (voterIdentity.isEmpty()) return this
    if (options.none { it.id == optionId }) return this
    return copy(votes = votes + (voterIdentity to optionId))
}

/** One option as the bubble draws it, counted against everyone who has voted so far. */
data class ChatPollResult(
    val option: ChatPollOption,
    val count: Int,
    /** Rounded to whole percent, so the options together may come to slightly more or less than 100. */
    val percent: Int,
    /** Identities, which the caller resolves to names — this layer knows nobody's name. */
    val voters: List<String>,
)

fun ChatPoll.results(): List<ChatPollResult> {
    val total = totalVotes
    return options.map { option ->
        val voters = votes.entries.filter { it.value == option.id }.map { it.key }
        ChatPollResult(
            option = option,
            count = voters.size,
            percent = if (total > 0) (voters.size * PercentScale / total).roundToInt() else 0,
            voters = voters,
        )
    }
}

private const val PercentScale = 100.0
