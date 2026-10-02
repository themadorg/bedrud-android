package com.bedrud.app.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsOn
import androidx.test.platform.app.InstrumentationRegistry
import com.bedrud.app.R
import com.bedrud.app.models.RoomSettings
import com.bedrud.app.ui.theme.BedrudTheme
import org.junit.Rule
import org.junit.Test

/** What [RoomSettingsForm] offers, and what it marks as not ready yet. Runs on a dev build. */
class RoomSettingsFormTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun showForm() {
        compose.setContent {
            BedrudTheme {
                var allowChat by remember { mutableStateOf(false) }
                RoomSettingsForm(
                    isPublic = false,
                    onIsPublicChange = {},
                    allowChat = allowChat,
                    onAllowChatChange = { allowChat = it },
                    roomSettings = RoomSettings(),
                )
            }
        }
    }

    @Test
    fun shouldMarkEveryLockedToggleComingSoon() {
        showForm()

        compose.onAllNodesWithText(context.getString(R.string.common_hint_comingSoon))
            .assertCountEquals(LockedToggleCount)
    }

    @Test
    fun shouldToggleChatFromItsLabel() {
        showForm()
        val label = context.getString(R.string.dashboard_roomSettings_allowChat)

        compose.onNodeWithText(label).performClick()

        compose.onNode(isToggleable() and hasText(label)).assertIsOn()
    }

    @Test
    fun shouldLeaveLockedToggleAloneWhenItsLabelIsTapped() {
        showForm()
        val label = context.getString(R.string.dashboard_roomSettings_e2ee)

        compose.onNodeWithText(label).performClick()

        compose.onNode(isToggleable() and hasText(label)).assertIsOff()
    }

    private companion object {
        /** Require approval, recording and end-to-end encryption. */
        const val LockedToggleCount = 3
    }
}
