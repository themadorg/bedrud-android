package com.bedrud.app.ui.screens.meeting

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import com.bedrud.app.R
import com.bedrud.app.testutil.FontScales
import com.bedrud.app.testutil.setThemedContentAt
import org.junit.Rule
import org.junit.Test

/**
 * What the picture-in-picture window shows of someone with no camera on, which in a voice call is
 * everyone. It used to be one large letter and nothing else.
 */
class MeetingPipViewTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun showParticipant(isMicOff: Boolean) {
        compose.setThemedContentAt(FontScales.Default) {
            MeetingPipView(
                state = ParticipantTileState(
                    identity = ParticipantIdentity,
                    name = ParticipantName,
                    avatarUrl = null,
                    cameraTrack = null,
                    screenShare = null,
                    isMicOff = isMicOff,
                ),
                room = null,
                showsWatchedStream = false,
            )
        }
    }

    @Test
    fun shouldNameTheParticipantShown() {
        showParticipant(isMicOff = false)

        compose.onNodeWithText(ParticipantName).assertExists()
    }

    @Test
    fun shouldMarkAMutedParticipantAsMuted() {
        showParticipant(isMicOff = true)

        compose.onNodeWithContentDescription(context.getString(R.string.meeting_contentDescription_micOff))
            .assertExists()
    }

    private companion object {
        const val ParticipantIdentity = "ada-identity"
        const val ParticipantName = "Ada Lovelace"
    }
}
