package com.bedrud.app.ui.screens.instance

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.platform.app.InstrumentationRegistry
import com.bedrud.app.R
import com.bedrud.app.testutil.FontScales
import com.bedrud.app.testutil.setThemedContentAt
import com.bedrud.app.ui.theme.Dimens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The add-server screen's two server-choice cards, stacked as the screen stacks them. */
class AddInstanceScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val officialTitle = context.getString(R.string.instance_choice_default_title)

    private val customTitle = context.getString(R.string.instance_choice_custom_title)

    private fun showBothCards(
        fontScale: Float = FontScales.Default,
        officialAddress: String = OfficialAddress,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
        address: String = "",
        isInsecure: Boolean = false,
    ) {
        compose.setThemedContentAt(fontScale) {
            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                Column {
                    OfficialServerCard(
                        selected = false,
                        onSelect = {},
                        badge = context.getString(R.string.instance_choice_default_tag),
                        address = officialAddress,
                    )
                    CustomServerCard(
                        selected = true,
                        onSelect = {},
                        value = address,
                        onValueChange = {},
                        focusRequester = remember { FocusRequester() },
                        onSubmit = {},
                        onScanQrCode = {},
                        isInsecure = isInsecure,
                    )
                }
            }
        }
    }

    // A card is selectable, which merges its title into the card's own node.
    private fun cardBounds(title: String): DpRect =
        compose.onNode(isSelectable() and hasText(title)).getUnclippedBoundsInRoot()

    private fun textBounds(text: String): DpRect =
        compose.onNodeWithText(text, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun fieldBounds(): DpRect = compose.onNode(hasSetTextAction()).getUnclippedBoundsInRoot()

    private fun scanButtonBounds(): DpRect = compose
        .onNodeWithContentDescription(context.getString(R.string.instance_contentDescription_scanQr))
        .getUnclippedBoundsInRoot()

    private fun titleOffsetInCard(title: String): Dp = textBounds(title).top - cardBounds(title).top

    private fun assertTitlesLevel(fontScale: Float, officialAddress: String = OfficialAddress) {
        showBothCards(fontScale, officialAddress)

        assertEquals(
            "titles sit at different heights at font scale $fontScale",
            titleOffsetInCard(officialTitle).value,
            titleOffsetInCard(customTitle).value,
            PositionTolerance.value,
        )
    }

    private fun assertAddressesEquallyFarUnderTitles(fontScale: Float) {
        showBothCards(fontScale)

        val officialGap = textBounds(OfficialAddress).top - textBounds(officialTitle).bottom
        val customGap = fieldBounds().top - textBounds(customTitle).bottom

        assertEquals(
            "addresses sit at different distances under their titles at font scale $fontScale",
            officialGap.value,
            customGap.value,
            PositionTolerance.value,
        )
    }

    private fun assertMessageInsideCustomCard(message: String) {
        val text = textBounds(message)
        val card = cardBounds(customTitle)

        assertTrue("\"$message\" starts above the address", text.top >= fieldBounds().bottom)
        assertTrue("\"$message\" ends outside its card", text.bottom <= card.bottom)
    }

    @Test
    fun shouldPlaceBothTitlesAtSameHeightInTheirCardsAtDefaultFontScale() {
        assertTitlesLevel(FontScales.Default)
    }

    // At this size a full-length official address wraps onto a second line, which makes its card's
    // content taller and centres its title higher; titles are level while both addresses take one
    // line, which is the case asserted here.
    @Test
    fun shouldPlaceBothTitlesAtSameHeightInTheirCardsAtLargestFontScaleWhenAddressesFitOneLine() {
        assertTitlesLevel(FontScales.Largest, ShortOfficialAddress)
    }

    @Test
    fun shouldSetOwnAddressAsFarUnderItsTitleAsOfficialAddressAtDefaultFontScale() {
        assertAddressesEquallyFarUnderTitles(FontScales.Default)
    }

    @Test
    fun shouldSetOwnAddressAsFarUnderItsTitleAsOfficialAddressAtRaisedFontScale() {
        assertAddressesEquallyFarUnderTitles(FontScales.Raised)
    }

    @Test
    fun shouldSetOwnAddressAsFarUnderItsTitleAsOfficialAddressAtLargestFontScale() {
        assertAddressesEquallyFarUnderTitles(FontScales.Largest)
    }

    @Test
    fun shouldGiveBothCardsTheMinimumCardHeight() {
        showBothCards()

        assertEquals(
            "official card height",
            Dimens.serverCardMinHeight.value,
            cardBounds(officialTitle).height.value,
            PositionTolerance.value,
        )
        assertEquals(
            "own-server card height",
            Dimens.serverCardMinHeight.value,
            cardBounds(customTitle).height.value,
            PositionTolerance.value,
        )
    }

    @Test
    fun shouldCentreOwnServerContentInItsCard() {
        showBothCards()
        val card = cardBounds(customTitle)

        // The scan button ends the content; the title text starts it, a little inside its own row.
        val roomAbove = textBounds(customTitle).top - card.top
        val roomBelow = card.bottom - scanButtonBounds().bottom

        assertEquals(
            "content is not centred in its card",
            roomBelow.value,
            roomAbove.value,
            TitleInsetAllowance.value,
        )
    }

    @Test
    fun shouldEndScanButtonAtCardContentEdge() {
        showBothCards()

        assertEquals(
            (cardBounds(customTitle).right - Dimens.cardPadding).value,
            scanButtonBounds().right.value,
            PositionTolerance.value,
        )
    }

    @Test
    fun shouldEndScanButtonAtCardContentEdgeInRightToLeftLayout() {
        showBothCards(layoutDirection = LayoutDirection.Rtl)

        assertEquals(
            (cardBounds(customTitle).left + Dimens.cardPadding).value,
            scanButtonBounds().left.value,
            PositionTolerance.value,
        )
    }

    @Test
    fun shouldCentreScanButtonOnAddressLine() {
        showBothCards()
        val scan = scanButtonBounds()
        val field = fieldBounds()

        assertEquals(
            ((field.top + field.bottom) / 2).value,
            ((scan.top + scan.bottom) / 2).value,
            PositionTolerance.value,
        )
    }

    @Test
    fun shouldShowInsecureNoteInsideCustomCard() {
        showBothCards(address = InsecureAddress, isInsecure = true)

        assertMessageInsideCustomCard(context.getString(R.string.instance_note_insecure))
    }

    private companion object {
        const val OfficialAddress = "https://bedrud.example"

        /** Short enough to stay on one line at the largest font scale. */
        const val ShortOfficialAddress = "https://b.xyz"

        const val InsecureAddress = "http://192.168.1.20:8080"

        /** Under a pixel at any density a test device has, so only a real misplacement fails. */
        val PositionTolerance = 0.5.dp

        /**
         * How far a title's text may sit inside its own row: the room above a centred card's
         * content is measured to the title's letters, not to the row, so it runs that much over
         * the room below. Content pinned to the top instead misses by the card's whole spare height.
         */
        val TitleInsetAllowance = 8.dp
    }
}
