package com.bedrud.app.ui.screens.meeting

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.bedrud.app.R
import com.bedrud.app.ui.theme.BedrudTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** How the participant sheet puts itself away once a moderation action is confirmed. */
class MeetingParticipantSheetTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    /** Shows the sheet to a room admin, counting how often it reports that it was dismissed. */
    private fun showSheetToAdmin(onDismiss: () -> Unit) {
        compose.setContent {
            BedrudTheme {
                MeetingParticipantSheet(
                    name = ParticipantName,
                    identity = ParticipantIdentity,
                    isAdmin = true,
                    roomId = RoomId,
                    roomApi = null,
                    snackbarHostState = SnackbarHostState(),
                    scope = rememberCoroutineScope(),
                    volume = 1f,
                    onVolumeChange = {},
                    isLocallyMuted = false,
                    onToggleLocalMute = {},
                    isVideoLocallyDisabled = false,
                    onToggleVideoDisabled = {},
                    isPinned = false,
                    isDeafened = false,
                    onTogglePin = {},
                    onFullscreen = {},
                    onDismiss = onDismiss,
                )
            }
        }
        compose.waitForIdle()
    }

    /**
     * Opens [action]'s confirmation from its row, confirms it, and checks the sheet is still up one
     * frame later and reports dismissal only once it has slid away.
     */
    private fun assertSlidesAwayAfterConfirming(action: String) {
        var dismissals = 0
        showSheetToAdmin(onDismiss = { dismissals++ })

        compose.onNodeWithText(action).performClick()
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        // The row and the dialog's confirm button share a label; only the button is a button.
        compose.onNode(hasText(action) and isButton).performClick()
        compose.mainClock.advanceTimeByFrame()

        assertEquals(0, dismissals)
        compose.onNodeWithText(ParticipantName).assertIsDisplayed()

        compose.mainClock.autoAdvance = true
        compose.waitForIdle()

        assertEquals(1, dismissals)
    }

    /**
     * Confirming a kick used to drop the sheet from composition in one frame, so it vanished behind
     * the closing dialog instead of sliding away like it does after every other action.
     */
    @Test
    fun shouldSlideAwayBeforeReportingDismissalAfterKick() {
        assertSlidesAwayAfterConfirming(context.getString(R.string.meeting_action_kick))
    }

    @Test
    fun shouldSlideAwayBeforeReportingDismissalAfterBan() {
        assertSlidesAwayAfterConfirming(context.getString(R.string.meeting_action_ban))
    }

    private companion object {
        const val ParticipantName = "Ada"
        const val ParticipantIdentity = "ada-identity"
        const val RoomId = "room-id"
    }
}
