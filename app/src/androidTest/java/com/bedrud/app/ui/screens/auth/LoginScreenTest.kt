package com.bedrud.app.ui.screens.auth

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import com.bedrud.app.R
import com.bedrud.app.core.auth.AuthManager
import com.bedrud.app.core.auth.SignInNoticeRelay
import com.bedrud.app.core.instance.InstanceManager
import com.bedrud.app.core.instance.InstanceStore
import com.bedrud.app.models.Instance
import com.bedrud.app.testutil.FontScales
import com.bedrud.app.testutil.setThemedContentAt
import com.bedrud.app.ui.components.BedrudButtonVariant
import com.bedrud.app.ui.screens.settings.SettingsStore
import org.junit.After
import org.junit.Rule
import org.junit.Test

/** The server being signed in to; `.invalid` never resolves, so nothing is fetched. */
private const val TEST_INSTANCE_ID = "login-screen-test-server"
private const val TEST_SERVER_URL = "https://example.invalid/"
private const val TEST_SERVER_NAME = "Example"

/** A file of its own, so the test never touches the servers the app has saved. */
private const val TEST_INSTANCES_PREFS = "login_screen_test_instances"

/** Long enough for a snackbar to appear, and to leave again once it has been read out. */
private const val SNACKBAR_TIMEOUT_MILLIS = 10_000L

/**
 * The sign-in hub: a sign-in method's button and the guest way in around it, with the server
 * allowing the method and without, and the reason a user was signed out.
 */
class LoginScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

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

    /** An [InstanceManager] whose only server has nobody signed in. */
    private fun signedOut(): InstanceManager {
        val instances = InstanceStore(context.getSharedPreferences(TEST_INSTANCES_PREFS, Context.MODE_PRIVATE))
        instances.addInstance(Instance(id = TEST_INSTANCE_ID, serverURL = TEST_SERVER_URL, displayName = TEST_SERVER_NAME))
        return InstanceManager(context.applicationContext as Application, instances, SettingsStore(context))
    }

    @After
    fun forgetServer() {
        AuthManager(context, TEST_INSTANCE_ID).logout()
        context.deleteSharedPreferences(TEST_INSTANCES_PREFS)
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

    @Test
    fun shouldSayWhyTheUserWasSignedOut() {
        // Signing out tears down the screen that did it, so the reason is only seen if the sign-in
        // screen the user lands on shows it.
        val notice = context.getString(R.string.auth_notice_passwordChanged)
        val relay = SignInNoticeRelay().apply { report(notice) }
        val instanceManager = signedOut()

        compose.setThemedContentAt(FontScales.Default) {
            LoginScreen(
                onLoginSuccess = {},
                onNavigateToEmailLogin = {},
                onNavigateToRegister = {},
                instanceManager = instanceManager,
                signInNoticeRelay = relay,
            )
        }

        compose.waitUntil(SNACKBAR_TIMEOUT_MILLIS) {
            compose.onAllNodes(hasText(notice)).fetchSemanticsNodes().isNotEmpty()
        }
        // Consumed once read out, so a later visit to the sign-in screen does not say it again.
        compose.waitUntil(SNACKBAR_TIMEOUT_MILLIS) { relay.message.value == null }
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
