package com.bedrud.app.ui.components

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.bedrud.app.testutil.FontScales
import com.bedrud.app.testutil.setThemedContentAt
import com.bedrud.app.ui.theme.Dimens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** How [BedrudButton] sizes itself around a label, at the reader's font size. */
class BedrudButtonTest {

    @get:Rule
    val compose = createComposeRule()

    private fun showButton(fontScale: Float, label: String) {
        compose.setThemedContentAt(fontScale) {
            BedrudButton(text = label, onClick = {}, modifier = Modifier.width(ButtonWidth))
        }
    }

    /** A label that wraps outgrows the minimum height, and nothing else keeps it off the edges. */
    @Test
    fun shouldPadWrappedLabelAwayFromEdgesAtLargestFontScale() {
        showButton(FontScales.Largest, WrappingLabel)

        val buttonTop = compose.onNode(hasClickAction()).getUnclippedBoundsInRoot().top
        val labelTop = compose.onNodeWithText(WrappingLabel, useUnmergedTree = true)
            .getUnclippedBoundsInRoot().top

        assertTrue(
            "label ${labelTop - buttonTop} from the button's top edge",
            labelTop - buttonTop >= Dimens.space8 - PositionTolerance,
        )
    }

    /** The padding must not grow a button whose label already fits its minimum height. */
    @Test
    fun shouldKeepMinimumHeightForOneLineLabelAtDefaultFontScale() {
        showButton(FontScales.Default, OneLineLabel)

        val height = compose.onNode(hasClickAction()).getUnclippedBoundsInRoot().height

        assertEquals(Dimens.buttonHeight.value, height.value, PositionTolerance.value)
    }

    private companion object {
        /** Narrow enough that [WrappingLabel] cannot fit one line at the largest font scale. */
        val ButtonWidth = 240.dp

        const val WrappingLabel = "Sign in with your email address"

        const val OneLineLabel = "Join"

        /** Bounds come back in fractional dp, so equal positions can differ by rounding alone. */
        val PositionTolerance = 0.5.dp
    }
}
