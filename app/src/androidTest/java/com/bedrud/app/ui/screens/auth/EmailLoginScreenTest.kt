package com.bedrud.app.ui.screens.auth

import android.app.Application
import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.platform.app.InstrumentationRegistry
import com.bedrud.app.R
import com.bedrud.app.core.auth.AuthManager
import com.bedrud.app.core.instance.InstanceManager
import com.bedrud.app.core.instance.InstanceStore
import com.bedrud.app.core.recent.RecentRoomsStore
import com.bedrud.app.models.Instance
import com.bedrud.app.testutil.FontScales
import com.bedrud.app.testutil.setThemedContentAt
import com.bedrud.app.ui.screens.settings.SettingsStore
import com.bedrud.app.ui.theme.BedrudTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test

/** The server being signed in to; `.invalid` never resolves, so no request gets through. */
private const val TEST_INSTANCE_ID = "email-login-screen-test-server"
private const val TEST_SERVER_URL = "https://example.invalid/"
private const val TEST_SERVER_NAME = "Example"

/** Files of their own, so the test never touches the servers or rooms the app has saved. */
private const val TEST_INSTANCES_PREFS = "email_login_screen_test_instances"
private const val TEST_RECENTS_PREFS = "email_login_screen_test_recents"

/** Long enough for the device to give up on the server and the message to appear. */
private const val MESSAGE_TIMEOUT_MILLIS = 10_000L

/** A password long enough for the app's policy, so the form lets it through. */
private const val PASSWORD = "the-password-1234"

/** How the email sign-in form and its password-reset sheet report a request that failed. */
class EmailLoginScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun showFailingResetSheet() {
        compose.setContent {
            BedrudTheme {
                ForgotPasswordSheet(
                    initialEmail = Email,
                    onDismiss = {},
                    onSubmit = { Result.failure(Exception(FailureMessage)) },
                    onSent = {},
                )
            }
        }
        compose.onNodeWithText(context.getString(R.string.auth_forgot_submit)).performClick()
    }

    private fun emailField() = compose.onNode(hasSetTextAction(), useUnmergedTree = true)

    /** The sign-in form, for a server that has nobody signed in. */
    private fun showEmailLogin() {
        val instances = InstanceStore(context.getSharedPreferences(TEST_INSTANCES_PREFS, Context.MODE_PRIVATE))
        instances.addInstance(Instance(id = TEST_INSTANCE_ID, serverURL = TEST_SERVER_URL, displayName = TEST_SERVER_NAME))
        val recentRooms = RecentRoomsStore(context.getSharedPreferences(TEST_RECENTS_PREFS, Context.MODE_PRIVATE))
        val instanceManager = InstanceManager(context.applicationContext as Application, instances, SettingsStore(context), recentRooms)
        compose.setThemedContentAt(FontScales.Default) {
            EmailLoginScreen(onLoginSuccess = {}, onBack = {}, instanceManager = instanceManager)
        }
    }

    private fun typeInto(@StringRes label: Int, text: String) {
        compose.onNodeWithText(context.getString(label)).performTextInput(text)
    }

    private fun tap(@StringRes label: Int) {
        compose.onNode(hasText(context.getString(label)) and hasClickAction()).performClick()
    }

    private fun waitForText(text: String) {
        compose.waitUntil(MESSAGE_TIMEOUT_MILLIS) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    @After
    fun forgetServer() {
        AuthManager(context, TEST_INSTANCE_ID).logout()
        context.deleteSharedPreferences(TEST_INSTANCES_PREFS)
        context.deleteSharedPreferences(TEST_RECENTS_PREFS)
    }

    /** The field's own supporting text, as every other form in the app reports an error. */
    @Test
    fun shouldReportFailureAsTheFieldsSupportingText() {
        showFailingResetSheet()

        emailField().assert(hasAnyDescendant(hasText(FailureMessage)))
    }

    @Test
    fun shouldMarkTheFieldInError() {
        showFailingResetSheet()

        emailField().assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error))
    }

    /**
     * Not "check your credentials", which would send the reader after a password that was never
     * tried, and not the device's own explanation, which is English and technical.
     */
    @Test
    fun shouldSayTheDeviceIsOfflineWhenSignInCannotFindTheServer() {
        showEmailLogin()
        typeInto(R.string.auth_label_email, Email)
        typeInto(R.string.auth_label_password, PASSWORD)

        tap(R.string.auth_button_signIn)

        waitForText(context.getString(R.string.error_network_unavailable))
    }

    @Test
    fun shouldSayTheDeviceIsOfflineWhenAResetLinkCannotBeSent() {
        showEmailLogin()
        typeInto(R.string.auth_label_email, Email)

        tap(R.string.auth_forgot_link)
        tap(R.string.auth_forgot_submit)

        waitForText(context.getString(R.string.error_network_unavailable))
    }

    private companion object {
        const val Email = "reset@example.test"
        const val FailureMessage = "Server unreachable"
    }
}
