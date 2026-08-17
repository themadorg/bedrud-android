package com.bedrud.app.core.meeting.chat

/** A link found in a message: what to open, and where it sits in the text. */
data class ChatLink(
    val url: String,
    val range: IntRange,
)

/**
 * Finds the links in a message.
 *
 * Hand-rolled rather than `android.util.Patterns.WEB_URL`, for the same reason the emoji test in
 * [isReactionEmoji] is hand-rolled: that constant lives in the Android framework, which is stubbed
 * out in local unit tests, and a chat message is worth being able to test without a device.
 *
 * Deliberately conservative at the edges, because the cost is asymmetric. Missing an exotic URL
 * leaves the reader doing what they do today — long-press, copy, paste. Linkifying ordinary prose
 * turns "see you Sept. 11" into a tappable target, and every tap on it is a wrong one. So a bare
 * host only counts when it carries a path: `bedrud.xyz/m/standup` is a link, `etc.` is not.
 */
fun findChatLinks(text: String): List<ChatLink> =
    LinkPattern.findAll(text).mapNotNull { match ->
        // A link ending a sentence swallows the punctuation that ended it, and a link inside
        // brackets swallows the closing one. Neither belongs to the URL.
        val trimmed = match.value.trimEnd(*TrailingPunctuation)
        if (trimmed.length < MinLinkLength) return@mapNotNull null
        ChatLink(
            url = trimmed,
            range = match.range.first until match.range.first + trimmed.length,
        )
    }.toList()

/**
 * Three shapes count as a link: one carrying its own scheme, one announcing itself with `www.`, and
 * a bare host followed by a path. Anything else — a bare host on its own, a scheme this app cannot
 * open — is left as the text it is.
 */
private val LinkPattern = Regex(
    """(?:https?://[^\s]+)""" +
        """|(?:www\.[^\s]+)""" +
        """|(?:[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?""" +
        """(?:\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)*""" +
        """\.[A-Za-z]{2,24}/[^\s]*)""",
)

/** Sentence and bracket punctuation that may sit against a link without belonging to it. */
private val TrailingPunctuation = charArrayOf(
    '.', ',', ';', ':', '!', '?', ')', ']', '}', '"', '\'', '»', '،', '؛', '؟',
)

/** Shorter than this and it is punctuation that happened to match, not an address. */
private const val MinLinkLength = 4
