package com.bedrud.app.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.bedrud.app.testutil.FontScales
import com.bedrud.app.testutil.setThemedContentAt
import com.bedrud.app.ui.theme.Dimens
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** How [BedrudSheetHandle] sizes itself and what it offers to touch and to a screen reader. */
class BedrudSheetHandleTest {

    @get:Rule
    val compose = createComposeRule()

    /** A sheet's handle keeps its roomy grab area above and below the bar. */
    @Test
    fun shouldPadBarByTwelveDpAboveAndBelowByDefault() {
        compose.setThemedContentAt(FontScales.Default) {
            BedrudSheetHandle(modifier = Modifier.testTag(HandleTag))
        }

        val height = compose.onNodeWithTag(HandleTag).getUnclippedBoundsInRoot().height

        assertEquals(
            (Dimens.meetingHandleHeight + Dimens.space12 * 2).value,
            height.value,
            PositionTolerance.value,
        )
    }

    /**
     * The call's controls bar sits the handle inside its pill, where 12dp would push the controls
     * down.
     */
    @Test
    fun shouldPadBarByGivenVerticalPadding() {
        compose.setThemedContentAt(FontScales.Default) {
            BedrudSheetHandle(
                modifier = Modifier.testTag(HandleTag),
                verticalPadding = Dimens.space4,
            )
        }

        val height = compose.onNodeWithTag(HandleTag).getUnclippedBoundsInRoot().height

        assertEquals(
            (Dimens.meetingHandleHeight + Dimens.space4 * 2).value,
            height.value,
            PositionTolerance.value,
        )
    }

    /** A handle a tap can act on says what the tap does. */
    @Test
    fun shouldAnnounceClickLabelWhenClickable() {
        compose.setThemedContentAt(FontScales.Default) {
            BedrudSheetHandle(onClick = {}, onClickLabel = ClickLabel)
        }

        compose.onNodeWithContentDescription(ClickLabel).assertHasClickAction()
    }

    /** A sheet with one height has nothing for a tap to do, so its handle offers no click. */
    @Test
    fun shouldOfferNoClickWithoutOnClick() {
        compose.setThemedContentAt(FontScales.Default) {
            BedrudSheetHandle(modifier = Modifier.testTag(HandleTag))
        }

        assertEquals(0, compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().size)
    }

    private companion object {
        const val HandleTag = "handle"

        const val ClickLabel = "More options"

        /**
         * The bar and each padding round to whole pixels on their own, so at a fractional density
         * the sum lands up to a pixel or so off the dp arithmetic — 0.57dp on a 2.625x screen.
         */
        val PositionTolerance = 1.dp
    }
}
