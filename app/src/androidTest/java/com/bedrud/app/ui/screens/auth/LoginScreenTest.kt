package com.bedrud.app.ui.screens.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.bedrud.app.testutil.FontScales
import com.bedrud.app.testutil.setThemedContentAt
import com.bedrud.app.ui.components.BedrudButtonVariant
import org.junit.Rule
import org.junit.Test

/**
 * A sign-in method's button on the sign-in hub, and the guest way in around it, with the server
 * allowing the method and without.
 */
class LoginScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun showMethod(serverAllows: Boolean, enabled: Boolean = true) {
        compose.setThemedContentAt(FontScales.Default) {
            Column {
                SignInMethodButton(
                    label = MethodLabel,
                    offLabel = OffLabel,
                    serverAllows = serverAllows,
                    enabled = enabled,
                    loading = false,
                    variant = BedrudButtonVariant.OUTLINE,
                    onClick = {},
                )
            }
        }
    }

    private fun showGuestSignIn(serverAllows: Boolean) {
        compose.setThemedContentAt(FontScales.Default) {
            Column {
                GuestSignIn(
                    name = GuestName,
                    onNameChange = {},
                    serverAllows = serverAllows,
                    enabled = true,
                    loading = false,
                    onContinue = {},
                )
            }
        }
    }

    @Test
    fun shouldNameMethodWhenServerAllowsIt() {
        showMethod(serverAllows = true)

        compose.onNodeWithText(MethodLabel).assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun shouldSayMethodIsOffOnItsButtonWhenServerTurnsItOff() {
        showMethod(serverAllows = false)

        compose.onNodeWithText(OffLabel).assertIsDisplayed().assertIsNotEnabled()
        compose.onAllNodesWithText(MethodLabel).assertCountEquals(0)
    }

    /** The button says it all: nothing is added under it. */
    @Test
    fun shouldDrawOnlyTheButtonWhenServerTurnsMethodOff() {
        showMethod(serverAllows = false)

        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)).assertCountEquals(1)
    }

    @Test
    fun shouldStayDisabledWhileOffEvenWhenOtherwiseReady() {
        showMethod(serverAllows = false, enabled = true)

        compose.onNodeWithText(OffLabel).assertIsNotEnabled()
    }

    @Test
    fun shouldStayDisabledWhileNotReadyEvenWhenServerAllowsIt() {
        showMethod(serverAllows = true, enabled = false)

        compose.onNodeWithText(MethodLabel).assertIsNotEnabled()
    }

    @Test
    fun shouldOfferGuestNameFieldWhenServerAllowsGuests() {
        showGuestSignIn(serverAllows = true)

        compose.onAllNodes(IsTextField).assertCountEquals(1)
    }

    /** With guest sign-in off there is no name to type, so no field asks for one. */
    @Test
    fun shouldLeaveOutGuestNameFieldWhenServerTurnsGuestsOff() {
        showGuestSignIn(serverAllows = false)

        compose.onAllNodes(IsTextField).assertCountEquals(0)
    }

    @Test
    fun shouldKeepGuestButtonWhenServerTurnsGuestsOff() {
        showGuestSignIn(serverAllows = false)

        compose.onAllNodes(hasClickAction()).assertCountEquals(1)
        compose.onNode(hasClickAction()).assertIsNotEnabled()
    }

    private companion object {
        const val MethodLabel = "Sign in with Passkey"
        const val OffLabel = "Passkey sign-in is off"
        const val GuestName = "Ada"

        /**
         * Any text field, enabled or not. A disabled field drops its set-text action, so matching
         * on that would miss a greyed field still sitting on screen.
         */
        val IsTextField = SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText)
    }
}
