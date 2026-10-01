package com.bedrud.app.ui.screens.meeting

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.bedrud.app.R
import com.bedrud.app.ui.theme.BedrudTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The handle on top of the call's controls, which opens the room options. */
class MeetingControlsPanelTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val moreOptions = context.getString(R.string.meeting_contentDescription_moreOptions)

    private fun showPanel(onExpandedChange: (Boolean) -> Unit = {}) {
        compose.setContent {
            BedrudTheme {
                Box {
                    MeetingControlsPanel(
                        expanded = false,
                        onExpandedChange = onExpandedChange,
                        isMicEnabled = true,
                        isCameraEnabled = false,
                        isScreenShareEnabled = false,
                        showChat = false,
                        unreadCount = 0,
                        isDeafened = false,
                        hideAllIncomingVideo = false,
                        isRoomSettingsAvailable = false,
                        onToggleMic = {},
                        onToggleCamera = {},
                        onToggleScreenShare = {},
                        onToggleChat = {},
                        onEndCall = {},
                        onToggleDeafen = {},
                        onToggleHideAllIncomingVideo = {},
                        onOpenAudioSettings = {},
                        onOpenNoiseSuppression = {},
                        onOpenRoomSettings = {},
                    )
                }
            }
        }
    }

    /** The handle used to be a 12dp strip, easy to miss and land on the video instead. */
    @Test
    fun shouldGiveHandleRoomToTapAboveItsBar() {
        showPanel()

        compose.onNodeWithContentDescription(moreOptions).assertHeightIsAtLeast(HandleTapStrip)
    }

    @Test
    fun shouldOpenOptionsWhenHandleIsTapped() {
        var requested: Boolean? = null
        showPanel(onExpandedChange = { requested = it })

        compose.onNodeWithContentDescription(moreOptions).performClick()

        assertEquals(true, requested)
    }

    private companion object {
        /** 8dp of air above the 4dp bar and 4dp below it, all of it tappable. */
        val HandleTapStrip = 16.dp
    }
}
