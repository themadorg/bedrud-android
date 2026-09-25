package com.bedrud.app.ui.screens.meeting

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.bedrud.app.core.BidiUtils
import com.bedrud.app.core.meeting.chat.ChatPoll
import com.bedrud.app.core.meeting.chat.ChatPollOption
import com.bedrud.app.testutil.FontScales
import com.bedrud.app.testutil.clipsLines
import com.bedrud.app.testutil.setThemedContentAt
import com.bedrud.app.testutil.textLayout
import com.bedrud.app.ui.theme.BedrudShapeTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

/** A poll's answers, when an answer is long enough to wrap. */
class MeetingChatPollTest {

    @get:Rule
    val compose = createComposeRule()

    private fun showPoll(fontScale: Float) {
        val poll = ChatPoll(
            id = "p-1",
            question = "When do we look at the numbers?",
            options = listOf(
                ChatPollOption(id = "o-1", text = ShortAnswer),
                ChatPollOption(id = "o-2", text = LongAnswer),
            ),
            votes = mapOf("u-1" to "o-2", "u-2" to "o-2", "u-me" to "o-1"),
        )
        compose.setThemedContentAt(fontScale) {
            ChatPollBubble(
                poll = poll,
                currentIdentity = "u-me",
                shape = BedrudShapeTokens.chatBubble(
                    isLocal = false,
                    tuckedAbove = false,
                    tuckedBelow = false,
                ),
                onVote = {},
                onShowResults = {},
            )
        }
    }

    /** Answers are drawn bidi-wrapped, so the node carries the wrapped form of the text. */
    private fun answerNode(answer: String): SemanticsNodeInteraction =
        compose.onNodeWithText(BidiUtils.wrap(answer), useUnmergedTree = true)

    @Test
    fun shouldLayOutWrappingAnswerInFullAtDefaultFontScale() {
        showPoll(FontScales.Default)

        val layout = answerNode(LongAnswer).textLayout()

        assertFalse("long answer clipped", layout.clipsLines)
    }

    @Test
    fun shouldLayOutWrappingAnswerInFullAtLargestFontScale() {
        showPoll(FontScales.Largest)

        val layout = answerNode(LongAnswer).textLayout()

        assertFalse("long answer clipped", layout.clipsLines)
    }

    /** A share squeezed by the answer beside it breaks between its digits. */
    @Test
    fun shouldKeepShareOnOneLineBesideWrappingAnswer() {
        showPoll(FontScales.Default)

        val layout = compose.onNodeWithText(LongAnswerShare, useUnmergedTree = true).textLayout()

        assertEquals("share squeezed by the answer beside it", 1, layout.lineCount)
    }

    private companion object {
        const val ShortAnswer = "Now"

        /** Long enough to run past two lines at the poll's fixed width. */
        const val LongAnswer =
            "Once the demo is over and everyone has had a chance to look at last week's figures"

        /** Two votes of three went to the long answer. */
        const val LongAnswerShare = "67%"
    }
}
