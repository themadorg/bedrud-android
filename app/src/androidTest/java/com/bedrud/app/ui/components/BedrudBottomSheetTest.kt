package com.bedrud.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.bedrud.app.ui.theme.BedrudTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** How a [BedrudBottomSheet] puts itself away when its own content closes it. */
class BedrudBottomSheetTest {

    @get:Rule
    val compose = createComposeRule()

    /**
     * A sheet that closed itself used to be dropped from composition in one frame, so it vanished
     * instead of sliding away. It now stays up while it slides, and only then reports dismissal.
     */
    @Test
    fun shouldSlideAwayBeforeReportingDismissal() {
        var dismissals = 0
        compose.setContent {
            BedrudTheme {
                BedrudBottomSheet(onDismiss = { dismissals++ }) {
                    Text(text = Pick, modifier = Modifier.clickable { dismiss() })
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false

        compose.onNodeWithText(Pick).performClick()
        compose.mainClock.advanceTimeByFrame()

        assertEquals(0, dismissals)
        compose.onNodeWithText(Pick).assertIsDisplayed()

        compose.mainClock.autoAdvance = true
        compose.waitForIdle()

        assertEquals(1, dismissals)
    }

    private companion object {
        const val Pick = "Pick"
    }
}
