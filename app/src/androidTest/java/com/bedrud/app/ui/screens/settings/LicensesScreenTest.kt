package com.bedrud.app.ui.screens.settings

import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.Locales
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.platform.app.InstrumentationRegistry
import com.bedrud.app.R
import com.bedrud.app.testutil.FontScales
import com.bedrud.app.testutil.setThemedContentAt
import com.bedrud.app.testutil.textLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** What [LicensesScreen] lists, read from the licence data the build generates. */
@OptIn(ExperimentalTestApi::class)
class LicensesScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private var backPressed = false

    private fun string(id: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    /**
     * Shows the screen and waits for its list, which is read off the main thread and so fills in a
     * moment after the screen appears.
     */
    private fun showScreen(locales: LocaleList = LocaleList(EnglishTag)) {
        compose.setThemedContentAt(FontScales.Default) {
            DeviceConfigurationOverride(DeviceConfigurationOverride.Locales(locales)) {
                LicensesScreen(onBack = { backPressed = true })
            }
        }
        compose.waitUntilAtLeastOneExists(hasText(LiveKitName), LoadTimeoutMillis)
    }

    private fun openVazirmatnLicence() {
        compose.onNodeWithText(VazirmatnName).performClick()
        compose.waitUntilAtLeastOneExists(hasText(VazirmatnCopyright, substring = true), LoadTimeoutMillis)
    }

    @Test
    fun shouldListMainProjectsFirst() {
        showScreen()

        listOf(LiveKitName, WebRtcName, VazirmatnName, RobotoName).forEach { name ->
            compose.onNodeWithText(name).assertExists()
        }
    }

    @Test
    fun shouldHoldOtherLibrariesBehindAllLibraries() {
        showScreen()

        compose.onNodeWithText(RetrofitName).assertDoesNotExist()
    }

    /** Libraries come from the dependency graph, so nobody has to remember to add one. */
    @Test
    fun shouldListEveryOtherLibraryFromAllLibraries() {
        showScreen()

        compose.onNodeWithText(string(R.string.settings_label_allLibraries)).performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(RetrofitName))

        compose.onNodeWithText(RetrofitName).assertExists()
    }

    /** OFL 1.1 asks for the font's copyright notice beside the licence text, not just a link. */
    @Test
    fun shouldShowFontCopyrightNoticeInItsLicence() {
        showScreen()

        openVazirmatnLicence()
    }

    /**
     * The texts are English, so in Persian or Arabic their punctuation must stay at each line's end.
     *
     * Asserts the direction the text is given rather than the one it is drawn in: left unset, the
     * running app drew the OFL's lines right to left in Persian, while this test's text resolved
     * left to right either way, so a check on the drawn result would pass with the bug in place.
     */
    @Test
    fun shouldPinLicenceTextLeftToRightInRightToLeftLanguage() {
        showScreen(LocaleList(PersianTag))

        openVazirmatnLicence()

        val layout = compose.onNode(hasText(VazirmatnCopyright, substring = true)).textLayout()
        assertEquals(LayoutDirection.Rtl, layout.layoutInput.layoutDirection)
        assertEquals(TextDirection.Ltr, layout.layoutInput.style.textDirection)
    }

    @Test
    fun shouldCloseLicenceFromCloseButton() {
        showScreen()
        openVazirmatnLicence()

        compose.onNodeWithText(string(R.string.settings_button_closeLicense)).performClick()

        compose.waitUntilDoesNotExist(hasText(VazirmatnCopyright, substring = true), LoadTimeoutMillis)
    }

    @Test
    fun shouldCallOnBackFromBackButton() {
        showScreen()

        compose.onNodeWithContentDescription(string(R.string.common_action_back)).performClick()

        assertTrue(backPressed)
    }

    private companion object {
        const val LoadTimeoutMillis = 5_000L

        const val EnglishTag = "en"

        const val PersianTag = "fa"

        const val VazirmatnName = "Vazirmatn"

        const val RobotoName = "Roboto"

        const val VazirmatnCopyright = "Copyright 2015 The Vazirmatn Project Authors"

        /** Names as each project's own POM gives them. */
        const val LiveKitName = "LiveKit Client Android SDK"

        const val WebRtcName = "WebRTC Android SDK"

        /** A library that is not one of the main projects. */
        const val RetrofitName = "Retrofit"
    }
}
