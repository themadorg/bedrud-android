package com.bedrud.app.ui.screens.auth

import android.app.Application
import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
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
import org.junit.After
import org.junit.Rule
import org.junit.Test

/** The server the account is created on; `.invalid` never resolves, so no request gets through. */
private const val TEST_INSTANCE_ID = "register-screen-test-server"
private const val TEST_SERVER_URL = "https://example.invalid/"
private const val TEST_SERVER_NAME = "Example"

/** Files of their own, so the test never touches the servers or rooms the app has saved. */
private const val TEST_INSTANCES_PREFS = "register_screen_test_instances"
private const val TEST_RECENTS_PREFS = "register_screen_test_recents"

/** Long enough for the device to give up on the server and the message to appear. */
private const val MESSAGE_TIMEOUT_MILLIS = 10_000L

private const val NAME = "Ada Lovelace"
private const val EMAIL = "ada@example.invalid"

/** The shortest password the app accepts, twelve characters, and one a character short of it. */
private const val SHORTEST_PASSWORD = "twelve-chars"
private const val PASSWORD_ONE_SHORT = "eleven-char"

/**
 * The sign-up form: what it lets through, what it flags, and how it reports a server it cannot reach.
 */
class RegisterScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** An [InstanceManager] whose only server has nobody signed in. */
    private fun signedOut(): InstanceManager {
        val instances = InstanceStore(context.getSharedPreferences(TEST_INSTANCES_PREFS, Context.MODE_PRIVATE))
        instances.addInstance(Instance(id = TEST_INSTANCE_ID, serverURL = TEST_SERVER_URL, displayName = TEST_SERVER_NAME))
        val recentRooms = RecentRoomsStore(context.getSharedPreferences(TEST_RECENTS_PREFS, Context.MODE_PRIVATE))
        return InstanceManager(context.applicationContext as Application, instances, SettingsStore(context), recentRooms)
    }

    private fun showRegister() {
        val instanceManager = signedOut()
        compose.setThemedContentAt(FontScales.Default) {
            RegisterScreen(onRegisterSuccess = {}, onNavigateToLogin = {}, instanceManager = instanceManager)
        }
    }

    /** Types [text] into the field labelled [label], scrolling it into view first. */
    private fun typeInto(@StringRes label: Int, text: String) {
        compose.onNodeWithText(context.getString(label)).performScrollTo().performTextInput(text)
    }

    private fun fillForm(name: String = NAME, email: String = EMAIL, password: String = SHORTEST_PASSWORD, confirmation: String = password) {
        typeInto(R.string.auth_label_displayName, name)
        typeInto(R.string.auth_label_email, email)
        typeInto(R.string.auth_label_password, password)
        typeInto(R.string.auth_label_confirmPassword, confirmation)
    }

    private fun createAccountButton() =
        compose.onNode(hasText(context.getString(R.string.auth_title_createAccount)) and hasClickAction())

    @After
    fun forgetServer() {
        AuthManager(context, TEST_INSTANCE_ID).logout()
        context.deleteSharedPreferences(TEST_INSTANCES_PREFS)
        context.deleteSharedPreferences(TEST_RECENTS_PREFS)
    }

    @Test
    fun shouldOfferCreatingTheAccountOnceEveryFieldIsValid() {
        showRegister()
        createAccountButton().assertIsNotEnabled()

        fillForm()

        createAccountButton().assertIsEnabled()
    }

    @Test
    fun shouldRefuseAPasswordOneCharacterShort() {
        showRegister()

        fillForm(password = PASSWORD_ONE_SHORT)

        createAccountButton().assertIsNotEnabled()
    }

    @Test
    fun shouldRefuseANameOfOnlySpaces() {
        showRegister()

        fillForm(name = "   ")

        createAccountButton().assertIsNotEnabled()
    }

    /** A pristine form stays calm: an email is only called malformed once something is typed. */
    @Test
    fun shouldFlagAMalformedEmailOnlyOnceTyped() {
        showRegister()
        compose.onNodeWithText(context.getString(R.string.auth_error_emailInvalid)).assertDoesNotExist()

        fillForm(email = "ada-at-example")

        compose.onNodeWithText(context.getString(R.string.auth_error_emailInvalid)).assertExists()
        createAccountButton().assertIsNotEnabled()
    }

    @Test
    fun shouldFlagAConfirmationThatDoesNotMatch() {
        showRegister()

        fillForm(confirmation = "$SHORTEST_PASSWORD!")

        compose.onNodeWithText(context.getString(R.string.auth_error_passwordMismatch)).assertExists()
        createAccountButton().assertIsNotEnabled()
    }

    /** The device's own explanation is English and technical; the reader is told in the app's words. */
    @Test
    fun shouldSayTheDeviceIsOfflineWhenTheServerCannotBeFound() {
        showRegister()
        fillForm()

        createAccountButton().performScrollTo().performClick()

        val offline = context.getString(R.string.error_network_unavailable)
        compose.waitUntil(MESSAGE_TIMEOUT_MILLIS) { compose.onAllNodesWithText(offline).fetchSemanticsNodes().isNotEmpty() }
    }
}
