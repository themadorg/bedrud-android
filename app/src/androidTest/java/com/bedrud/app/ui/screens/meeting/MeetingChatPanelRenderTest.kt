package com.bedrud.app.ui.screens.meeting

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.bedrud.app.R
import com.bedrud.app.core.livekit.ChatMessage
import com.bedrud.app.core.meeting.chat.ChatPoll
import com.bedrud.app.core.meeting.chat.ChatPollOption
import com.bedrud.app.core.meeting.chat.QuickReactions
import com.bedrud.app.ui.theme.BedrudTheme
import java.io.File
import org.junit.Rule
import org.junit.Test

/**
 * Renders the chat panel against fixed data and writes what it drew to the device.
 *
 * Not an assertion test: composing at all is the check, and the images are what a reviewer looks
 * at. Every name here is invented, so the captures carry nothing belonging to a real account.
 */
class MeetingChatPanelRenderTest {

    @get:Rule
    val compose = createComposeRule()

    private fun messages(): List<ChatMessage> {
        val start = 1_700_000_000_000L
        var id = 0
        fun at(minutes: Long) = start + minutes * 60_000
        fun message(
            name: String,
            identity: String,
            text: String,
            minutes: Long,
            local: Boolean,
            reactions: Map<String, String> = emptyMap(),
            poll: ChatPoll? = null,
        ) =
            ChatMessage(
                id = "m${id++}",
                senderName = name,
                senderIdentity = identity,
                text = text,
                timestamp = at(minutes),
                isLocal = local,
                reactions = reactions,
                poll = poll,
            )
        return listOf(
            message("سارا احمدی", "u-1", "سلام، صدای من رو دارید؟", 0, false),
            message("سارا احمدی", "u-1", "دوربینم رو الان روشن می‌کنم", 0, false),
            message("You", "u-me", "Yes, coming through clearly.", 1, true),
            message(
                "You", "u-me", "Give me a second to share the deck.", 1, true,
                reactions = mapOf("u-1" to "👍", "u-2" to "👍"),
            ),
            message("Miriam Okonkwo", "u-2", "Take your time 🙂", 2, false),
            message(
                "Miriam Okonkwo", "u-2", "I have the numbers from last week open too", 2, false,
                reactions = mapOf("u-me" to "🔥", "u-1" to "🎉"),
            ),
            message("Miriam Okonkwo", "u-2", "so shout if you want them on screen", 2, false),
            message(
                "Miriam Okonkwo", "u-2", "", 3, false,
                poll = ChatPoll(
                    id = "p-1",
                    question = "Do we run the numbers now, or after the demo?",
                    options = listOf(
                        ChatPollOption(id = "o-1", text = "Now"),
                        ChatPollOption(id = "o-2", text = "After the demo"),
                        ChatPollOption(id = "o-3", text = "Next call"),
                    ),
                    votes = mapOf("u-1" to "o-2", "u-2" to "o-2", "u-me" to "o-1"),
                ),
            ),
            message("You", "u-me", "Perfect, thanks.", 9, true),
        )
    }

    /** The fixture's own names, so a breakdown shows people rather than identities. */
    private fun fixtureName(identity: String): String = when (identity) {
        "u-1" -> "سارا احمدی"
        "u-2" -> "Miriam Okonkwo"
        else -> identity
    }

    @Test
    fun chatPanel() {
        capture("chat-panel") {
            Panel(canParticipate = true)
        }
    }

    /** Blocked from sending: the conversation stays readable, its reactions stop answering. */
    @Test
    fun chatPanelWithSendingBlocked() {
        capture("chat-panel-blocked") {
            Panel(canParticipate = false)
        }
    }

    /**
     * The message menu, opened the way a reader opens it. Long-pressing a message has to reach the
     * menu rather than the list under it, so this is a check as much as a capture — and the capture
     * has to show all three of the pill, the bubble and the actions card, which is the whole point
     * of the menu being two surfaces instead of one.
     */
    @Test
    fun chatMessageMenu() {
        capture(
            "chat-message-menu",
            windowMarker = QuickReactions.first(),
            beforeCapture = {
                // Substring, not exact: the bubble renders the message bidi-wrapped, and on an
                // RTL-locale device the wrap brackets this Latin text in invisible isolate marks
                // that an exact match trips over.
                compose.onNodeWithText(LongPressedMessage, substring = true)
                    .performTouchInput { longClick() }
            },
        ) {
            Panel(canParticipate = true)
        }
    }

    /** Who reacted, behind the menu's reactions row. */
    @Test
    fun chatReactions() {
        val reactions = messages().first { it.reactions.isNotEmpty() }.reactions
        capture("chat-reactions", windowMarker = localizedString(R.string.meeting_chat_reactions)) {
            ChatReactionsSheet(
                reactions = reactions,
                currentIdentity = "u-me",
                resolveName = ::fixtureName,
                onDismiss = {},
            )
        }
    }

    /** The poll composer, which lives in a sheet of its own rather than in the panel. */
    @Test
    fun chatPollComposer() {
        capture("chat-poll-composer", windowMarker = localizedString(R.string.meeting_chat_poll_new)) {
            MeetingChatPollSheet(onDismiss = {}, onCreate = {})
        }
    }

    /** The voter breakdown behind a poll's "view results". */
    @Test
    fun chatPollResults() {
        val poll = messages().first { it.poll != null }.poll!!
        capture("chat-poll-results", windowMarker = poll.question) {
            ChatPollResultsSheet(
                poll = poll,
                currentIdentity = "u-me",
                resolveName = ::fixtureName,
                onDismiss = {},
            )
        }
    }

    /**
     * The conversation paints no background of its own — the controls panel recedes to
     * `surfaceContainerLow` behind it — so the capture supplies the same ground the panel would.
     */
    @androidx.compose.runtime.Composable
    private fun Panel(canParticipate: Boolean) {
      androidx.compose.material3.Surface(
        color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxSize(),
      ) {
        MeetingChatConversation(
            messages = messages(),
            currentIdentity = "u-me",
            composer = rememberMeetingChatComposerState(
                imageContext = null,
                input = "",
                onInputChange = {},
                onSendAttachment = { _, _ -> },
            ),
            onToggleReaction = { _, _ -> },
            onVote = { _, _ -> },
            onSendPoll = {},
            resolveName = { it },
            imageContext = null,
            canParticipate = canParticipate,
            knownHosts = emptySet(),
            modifier = Modifier.fillMaxSize(),
        )
      }
    }

    /**
     * [windowMarker] is text the capture must contain, for anything drawn in a window of its own —
     * a sheet, a dialog. There is more than one root then: asking for "the" root throws, and taking
     * the newest one catches the scrim, which is why the composer first captured as a blank page.
     * Waiting for the text also waits out the sheet's entrance, so nothing is caught mid-animation.
     */
    private fun capture(
        name: String,
        windowMarker: String? = null,
        beforeCapture: (() -> Unit)? = null,
        content: @androidx.compose.runtime.Composable () -> Unit,
    ) {
        compose.setContent {
            BedrudTheme(darkTheme = true) { content() }
        }
        compose.waitForIdle()
        beforeCapture?.invoke()

        val bitmap = if (windowMarker == null) {
            compose.onRoot().captureToImage().asAndroidBitmap()
        } else {
            // A sheet draws in a window of its own, and Compose's own capture comes back blank for
            // it. Waiting for the sheet's text also waits out its entrance; the display screenshot
            // then catches every window at once, sheet and scrim together.
            compose.waitUntil(SheetTimeoutMillis) {
                // Substring: rendered text may be bidi-wrapped (see the long-press above).
                compose.onAllNodesWithText(windowMarker, substring = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }
            compose.waitForIdle()
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        }
        File(captureDir(), "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    /**
     * A sheet's title in whatever language the device speaks — resolved from resources rather
     * than written as an English literal, so the wait works on a Persian-locale device too.
     */
    private fun localizedString(resId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resId)

    private companion object {
        /** Long enough for a sheet to finish arriving on a cold emulator, short enough to fail fast. */
        const val SheetTimeoutMillis = 5_000L

        /** The message the picker is opened on — one of the fixture's, and short enough to hit. */
        const val LongPressedMessage = "Take your time 🙂"
    }

    /**
     * Where the captures are written.
     *
     * The test runner hands over a directory it copies back to the build output when the run ends,
     * which is the only path off a modern device that does not need root: an app's own external
     * files directory is unreadable from the shell since scoped storage, so images written there
     * stay on the device where no reviewer can see them. The fallback keeps the test working when
     * it is run from an IDE, which passes no such argument.
     */
    private fun captureDir(): File {
        val fromRunner = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        val dir = fromRunner?.let(::File)
            ?: InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)!!
        dir.mkdirs()
        return dir
    }
}
