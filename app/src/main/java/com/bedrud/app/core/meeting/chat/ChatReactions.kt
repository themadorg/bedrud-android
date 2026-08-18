package com.bedrud.app.core.meeting.chat

/**
 * The reactions on one message: the emoji each person chose, keyed by their identity.
 *
 * One person holds one reaction. Choosing another replaces it, choosing the same one again takes it
 * back. That is the whole model the other clients implement, so it is the whole model here.
 */
typealias ChatReactions = Map<String, String>

/**
 * What the picker offers, in the order it draws them — the same set the other clients use, so a
 * room reacts with one vocabulary instead of two.
 */
val QuickReactions: List<String> = listOf("👍", "❤️", "😂", "😮", "😢", "🎉", "🔥", "👀")

/**
 * Whether this is something worth drawing as a reaction.
 *
 * A recognition test rather than a full Unicode property check: `\p{Extended_Pictographic}` exists
 * in Android's regex but not in the JVM the unit tests run on, and a reaction row is not worth a
 * dependency. It only has to keep a remote client's stray text out of the chip row — every reaction
 * this app sends comes from [QuickReactions].
 */
fun isReactionEmoji(emoji: String): Boolean {
    if (emoji.isEmpty() || emoji.length > MaxReactionLength) return false
    var index = 0
    while (index < emoji.length) {
        val codePoint = emoji.codePointAt(index)
        if (EmojiRanges.any { codePoint in it }) return true
        index += Character.charCount(codePoint)
    }
    return false
}

/**
 * Adds, replaces or removes this person's reaction, which is the one thing a tap can mean: the same
 * emoji again takes it back, a different one moves their reaction to it.
 */
fun ChatReactions.toggled(voterIdentity: String, emoji: String): ChatReactions {
    if (voterIdentity.isEmpty() || !isReactionEmoji(emoji)) return this
    return if (this[voterIdentity] == emoji) this - voterIdentity else this + (voterIdentity to emoji)
}

/** One chip under a bubble: an emoji, how many chose it, and whether the reader is among them. */
data class GroupedReaction(
    val emoji: String,
    val count: Int,
    val mine: Boolean,
)

/**
 * Collapses the per-person reactions into the chips the bubble draws, most-chosen first.
 *
 * Chips on equal counts keep the order they were first reacted with — the sort is stable and the
 * reactions arrive in the order they were cast — so the row does not reshuffle itself every time
 * somebody joins a tie.
 */
fun ChatReactions.grouped(currentIdentity: String): List<GroupedReaction> {
    if (isEmpty()) return emptyList()
    val counts = LinkedHashMap<String, GroupedReaction>()
    forEach { (identity, emoji) ->
        if (!isReactionEmoji(emoji)) return@forEach
        val existing = counts[emoji]
        counts[emoji] = GroupedReaction(
            emoji = emoji,
            count = (existing?.count ?: 0) + 1,
            mine = existing?.mine == true || identity == currentIdentity,
        )
    }
    return counts.values.sortedByDescending { it.count }
}

/** One section of the breakdown: an emoji, and everyone who chose it. */
data class ReactionBreakdown(
    val emoji: String,
    val identities: List<String>,
)

/**
 * Who reacted with what, most-chosen first — the same order as the chips under the bubble, so the
 * breakdown reads as those chips opened up rather than as a second, differently sorted list.
 */
fun ChatReactions.breakdown(): List<ReactionBreakdown> {
    if (isEmpty()) return emptyList()
    val byEmoji = LinkedHashMap<String, MutableList<String>>()
    forEach { (identity, emoji) ->
        if (!isReactionEmoji(emoji)) return@forEach
        byEmoji.getOrPut(emoji) { mutableListOf() }.add(identity)
    }
    return byEmoji.map { (emoji, identities) -> ReactionBreakdown(emoji, identities) }
        .sortedByDescending { it.identities.size }
}

/** Longest reaction accepted. A flag with modifiers is still a fraction of this. */
private const val MaxReactionLength = 32

/**
 * The code point ranges that count as emoji here, from the ones Unicode gives emoji presentation.
 * Deliberately coarse at the edges: letting one odd symbol through costs a strange-looking chip,
 * where being too strict would silently drop a reaction someone really sent.
 */
private val EmojiRanges = listOf(
    0x00A9..0x00AE,   // copyright, registered
    0x203C..0x2049,   // double exclamation, interrobang
    0x2122..0x2122,   // trade mark
    0x2139..0x2139,   // information
    0x2194..0x21AA,   // arrows drawn as emoji
    0x231A..0x231B,   // watch, hourglass
    0x2328..0x2328,   // keyboard
    0x23CF..0x23FA,   // eject, media controls, clock faces
    0x24C2..0x24C2,   // circled M
    0x25AA..0x25FE,   // small geometric shapes
    0x2600..0x27BF,   // miscellaneous symbols and dingbats
    0x2934..0x2935,   // curved arrows
    0x2B00..0x2BFF,   // arrows and geometric shapes
    0x3030..0x3030,   // wavy dash
    0x303D..0x303D,   // part alternation mark
    0x3297..0x3299,   // circled ideographs
    0x1F000..0x1FAFF, // the emoji blocks proper
)
