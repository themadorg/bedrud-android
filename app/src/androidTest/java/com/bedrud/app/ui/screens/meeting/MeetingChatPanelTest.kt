package com.bedrud.app.ui.screens.meeting

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.bedrud.app.R
import com.bedrud.app.core.deeplink.ChatLinkTarget
import com.bedrud.app.core.livekit.ChatMessage
import com.bedrud.app.models.Instance
import com.bedrud.app.ui.theme.BedrudTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** What tapping a room link in the chat does, for each place the link can lead. */
class MeetingChatPanelTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** A panel holding one message that is nothing but [RoomLink], so a tap on the message is a tap on the link. */
    private fun showPanel(target: ChatLinkTarget, onFollowRoom: (ChatLinkTarget.Room) -> Unit = {}) {
        compose.setContent {
            BedrudTheme {
                MeetingChatPanel(
                    messages = listOf(
                        ChatMessage(
                            id = "m-1",
                            senderName = "Miriam Okonkwo",
                            senderIdentity = "u-2",
                            text = RoomLink,
                            timestamp = 1_700_000_000_000L,
                            isLocal = false,
                        ),
                    ),
                    input = "",
                    currentIdentity = "u-me",
                    onInputChange = {},
                    onSend = {},
                    onSendAttachment = { _, _ -> },
                    onSendPoll = {},
                    onToggleReaction = { _, _ -> },
                    onVote = { _, _ -> },
                    resolveName = { it },
                    imageContext = null,
                    sendDisabledReason = null,
                    resolveLink = { target },
                    onFollowRoom = onFollowRoom,
                )
            }
        }
    }

    @Test
    fun shouldSayTheReaderIsAlreadyThereWhenLinkIsToCurrentRoom() {
        showPanel(ChatLinkTarget.CurrentRoom)

        compose.onNodeWithText(RoomLink, substring = true).performClick()

        compose.onNodeWithText(context.getString(R.string.meeting_chat_linkCurrentRoom)).assertIsDisplayed()
    }

    @Test
    fun shouldAskToFollowLinkToAnotherRoom() {
        val target = ChatLinkTarget.Room(NextRoom, Server, switchesServer = false)
        var followed: ChatLinkTarget.Room? = null
        showPanel(target, onFollowRoom = { followed = it })

        compose.onNodeWithText(RoomLink, substring = true).performClick()

        assertEquals(target, followed)
    }

    private companion object {
        const val NextRoom = "oqc-qpka-bvw"
        const val RoomLink = "https://bedrud.xyz/m/$NextRoom"
        val Server = Instance(id = "active", serverURL = "https://bedrud.xyz/", displayName = "Bedrud")
    }
}
