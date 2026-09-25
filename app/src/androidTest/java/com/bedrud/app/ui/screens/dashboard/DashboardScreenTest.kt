package com.bedrud.app.ui.screens.dashboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.height
import androidx.test.platform.app.InstrumentationRegistry
import com.bedrud.app.R
import com.bedrud.app.testutil.FontScales
import com.bedrud.app.testutil.clipsLines
import com.bedrud.app.testutil.setThemedContentAt
import com.bedrud.app.testutil.textLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

/** The quick-join bar at the font scales a reader can choose in system settings. */
class DashboardScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun showQuickJoinBar(fontScale: Float, value: String = "") {
        compose.setThemedContentAt(fontScale) {
            QuickJoinBar(value = value, onValueChange = {}, onJoin = {})
        }
    }

    private fun fieldHeight(): Dp = compose.onNode(hasSetTextAction()).getUnclippedBoundsInRoot().height

    private fun assertPlaceholderFits(fontScale: Float) {
        showQuickJoinBar(fontScale)
        val placeholder = context.getString(R.string.dashboard_placeholder_search)

        val layout = compose.onNodeWithText(placeholder, useUnmergedTree = true).textLayout()

        assertFalse("placeholder clipped at font scale $fontScale", layout.clipsLines)
    }

    @Test
    fun shouldLayOutPlaceholderInFullAtDefaultFontScale() {
        assertPlaceholderFits(FontScales.Default)
    }

    @Test
    fun shouldLayOutPlaceholderInFullAtRaisedFontScale() {
        assertPlaceholderFits(FontScales.Raised)
    }

    @Test
    fun shouldLayOutPlaceholderInFullAtLargeFontScale() {
        assertPlaceholderFits(FontScales.Large)
    }

    @Test
    fun shouldLayOutPlaceholderInFullAtLargestFontScale() {
        assertPlaceholderFits(FontScales.Largest)
    }

    @Test
    fun shouldLayOutTypedRoomNameInFullAtLargestFontScale() {
        showQuickJoinBar(FontScales.Largest, value = TypedRoomName)

        val layout = compose.onNode(hasSetTextAction()).textLayout()

        assertFalse("typed room name clipped", layout.clipsLines)
    }

    @Test
    fun shouldKeepJoinButtonAsTallAsFieldAtLargestFontScale() {
        showQuickJoinBar(FontScales.Largest, value = TypedRoomName)
        val joinLabel = context.getString(R.string.common_button_join)

        val buttonHeight = compose.onNode(hasText(joinLabel) and hasClickAction())
            .getUnclippedBoundsInRoot().height

        assertEquals(fieldHeight().value, buttonHeight.value, HeightToleranceDp)
    }

    /** Whatever sits under the bar would jump if the field changed height on the first keystroke. */
    @Test
    fun shouldKeepFieldHeightWhenTypingStartsAtLargestFontScale() {
        var value by mutableStateOf("")
        compose.setThemedContentAt(FontScales.Largest) {
            QuickJoinBar(value = value, onValueChange = {}, onJoin = {})
        }
        val emptyHeight = fieldHeight()

        compose.runOnIdle { value = TypedRoomName }
        val typedHeight = fieldHeight()

        assertEquals(emptyHeight.value, typedHeight.value, HeightToleranceDp)
    }

    private companion object {
        const val TypedRoomName = "weekly-sync"

        /** Bounds come back in fractional dp, so two equal heights can differ by rounding alone. */
        const val HeightToleranceDp = 0.5f
    }
}
