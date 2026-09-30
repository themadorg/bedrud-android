package com.bedrud.app.ui.screens.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.bedrud.app.testutil.FontScales
import com.bedrud.app.testutil.setThemedContentAt
import com.bedrud.app.ui.components.BedrudButtonVariant
import org.junit.Rule
import org.junit.Test

/** A sign-in method's button on the sign-in hub, with the server allowing the method and without. */
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

    private companion object {
        const val MethodLabel = "Sign in with Passkey"
        const val OffLabel = "Passkey sign-in is off"
    }
}
