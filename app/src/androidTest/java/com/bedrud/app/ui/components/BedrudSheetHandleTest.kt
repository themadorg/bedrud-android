package com.bedrud.app.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.bedrud.app.ui.theme.BedrudTheme
import com.bedrud.app.ui.theme.Dimens
import org.junit.Rule
import org.junit.Test

/** How much room [BedrudSheetHandle] gives a finger, with and without a tap action. */
class BedrudSheetHandleTest {

    @get:Rule
    val compose = createComposeRule()

    /** A sheet with one height still lets its handle be grabbed without hunting for a 4dp bar. */
    @Test
    fun shouldGivePlainHandleFullTouchTargetHeight() {
        compose.setContent {
            BedrudTheme { BedrudSheetHandle(modifier = Modifier.testTag(HandleTag)) }
        }

        compose.onNodeWithTag(HandleTag).assertHeightIsAtLeast(Dimens.minTouchTarget)
    }

    @Test
    fun shouldGiveTappableHandleFullTouchTargetHeight() {
        compose.setContent {
            BedrudTheme { BedrudSheetHandle(onClick = {}, onClickLabel = HandleLabel) }
        }

        compose.onNode(hasClickAction()).assertHeightIsAtLeast(Dimens.minTouchTarget)
    }

    @Test
    fun shouldGiveTappableHandleFullTouchTargetWidth() {
        compose.setContent {
            BedrudTheme { BedrudSheetHandle(onClick = {}, onClickLabel = HandleLabel) }
        }

        compose.onNode(hasClickAction()).assertWidthIsAtLeast(Dimens.minTouchTarget)
    }

    private companion object {
        const val HandleTag = "sheetHandle"

        const val HandleLabel = "Expand"
    }
}
