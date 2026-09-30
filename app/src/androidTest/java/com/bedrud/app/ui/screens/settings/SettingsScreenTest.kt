package com.bedrud.app.ui.screens.settings

import android.app.Application
import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.bedrud.app.R
import com.bedrud.app.core.auth.AuthManager
import com.bedrud.app.core.auth.SignInMethod
import com.bedrud.app.core.instance.InstanceManager
import com.bedrud.app.core.instance.InstanceStore
import com.bedrud.app.models.Instance
import com.bedrud.app.models.User
import com.bedrud.app.testutil.FontScales
import com.bedrud.app.testutil.setThemedContentAt
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The server the test's account is signed in to; `.invalid` never resolves, so nothing is fetched. */
private const val TEST_INSTANCE_ID = "settings-screen-test-account"
private const val TEST_SERVER_URL = "https://example.invalid/"
private const val TEST_SERVER_NAME = "Example"

/** When a user's password last changed, as the server writes the time. */
private const val PASSWORD_CHANGED_AT = "2026-09-01T10:00:00Z"

/** Passwords long enough for the app's policy. */
private const val CURRENT_PASSWORD = "the-current-password-1234"
private const val NEW_PASSWORD = "a-new-password-1234"

/** A file of its own, so the test never touches the servers the app has saved. */
private const val TEST_INSTANCES_PREFS = "settings_screen_test_instances"

/** How far apart two widths may be and still count as equal, in pixels. */
private const val WIDTH_TOLERANCE_PX = 0.5f

class SettingsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** Resolves the Security card's sentence for [method] the way the card itself does. */
    private fun passwordUnavailableMessageFor(method: SignInMethod): String {
        var message = ""
        compose.setContent { message = passwordUnavailableMessage(method) }
        compose.waitForIdle()
        return message
    }

    /** An [InstanceManager] whose only server has [user] signed in. */
    private fun signedIn(user: User): InstanceManager {
        val instances = InstanceStore(context.getSharedPreferences(TEST_INSTANCES_PREFS, Context.MODE_PRIVATE))
        instances.addInstance(Instance(id = TEST_INSTANCE_ID, serverURL = TEST_SERVER_URL, displayName = TEST_SERVER_NAME))
        AuthManager(context, TEST_INSTANCE_ID).saveUser(user)
        return InstanceManager(context.applicationContext as Application, instances, SettingsStore(context))
    }

    /** An [InstanceManager] whose only server has a guest signed in. */
    private fun signedInGuest(): InstanceManager =
        signedIn(User(id = TEST_INSTANCE_ID, email = "", name = TEST_SERVER_NAME, provider = "guest"))

    /** An [InstanceManager] whose only server has a passkey account signed in. */
    private fun signedInPasskeyAccount(passwordChangedAt: String?): InstanceManager =
        signedIn(
            User(
                id = TEST_INSTANCE_ID,
                email = "passkey@example.invalid",
                name = TEST_SERVER_NAME,
                provider = "passkey",
                passwordChangedAt = passwordChangedAt
            )
        )

    /** Shows Settings for whoever [instanceManager] has signed in. */
    private fun showSettings(instanceManager: InstanceManager) {
        compose.setThemedContentAt(FontScales.Default) {
            SettingsContent(settingsStore = SettingsStore(context), instanceManager = instanceManager)
        }
    }

    /** Types [text] into the field labelled [label], scrolling it into view first. */
    private fun typeInto(@StringRes label: Int, text: String) {
        compose.onNodeWithText(context.getString(label)).performScrollTo().performTextInput(text)
    }

    /** The width of the card whose section header reads [header]. */
    private fun cardWidth(@StringRes header: Int): Float {
        val headerNode = compose.onNodeWithText(context.getString(header), useUnmergedTree = true).fetchSemanticsNode()
        return checkNotNull(headerNode.parent).boundsInRoot.width
    }

    @After
    fun forgetAccount() {
        AuthManager(context, TEST_INSTANCE_ID).logout()
        context.deleteSharedPreferences(TEST_INSTANCES_PREFS)
    }

    @Test
    fun shouldGiveGuestItsOwnPasswordUnavailableSentence() {
        // A guest's method dropped into the identity-provider sentence cannot agree with it in
        // every language, so a guest is told in a sentence of its own.
        assertEquals(
            context.getString(R.string.settings_password_unavailable_guest),
            passwordUnavailableMessageFor(SignInMethod.Guest)
        )
    }

    @Test
    fun shouldNameIdentityProviderInPasswordUnavailableSentence() {
        assertEquals(
            context.getString(R.string.settings_password_unavailable, "Google"),
            passwordUnavailableMessageFor(SignInMethod.Provider("Google"))
        )
    }

    @Test
    fun shouldDrawGuestSecurityCardAsWideAsAppearanceCard() {
        // With no password form in it, the Security card holds one short sentence; sized to that,
        // it stood narrower than every other card on the page.
        showSettings(signedInGuest())

        assertEquals(
            cardWidth(R.string.settings_section_appearance),
            cardWidth(R.string.settings_section_security),
            WIDTH_TOLERANCE_PX
        )
    }

    @Test
    fun shouldLetPasskeyAccountWithoutPasswordSetOneWithoutCurrentPassword() {
        // The account has no password to type, and the server does not ask for one; a required
        // current-password field left the button disabled until the user made one up.
        showSettings(signedInPasskeyAccount(passwordChangedAt = null))

        compose.onNodeWithText(context.getString(R.string.settings_label_currentPassword)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.settings_password_setIntro)).assertExists()
        typeInto(R.string.settings_label_newPassword, NEW_PASSWORD)
        typeInto(R.string.settings_label_confirmNewPassword, NEW_PASSWORD)
        compose.onNodeWithText(context.getString(R.string.settings_button_setPassword)).assertIsEnabled()
    }

    @Test
    fun shouldAskPasskeyAccountWithPasswordForCurrentOneBeforeChangingIt() {
        showSettings(signedInPasskeyAccount(passwordChangedAt = PASSWORD_CHANGED_AT))

        compose.onNodeWithText(context.getString(R.string.settings_label_currentPassword)).assertExists()
        typeInto(R.string.settings_label_newPassword, NEW_PASSWORD)
        typeInto(R.string.settings_label_confirmNewPassword, NEW_PASSWORD)
        compose.onNodeWithText(context.getString(R.string.settings_button_changePassword)).assertIsNotEnabled()
        typeInto(R.string.settings_label_currentPassword, CURRENT_PASSWORD)
        compose.onNodeWithText(context.getString(R.string.settings_button_changePassword)).assertIsEnabled()
    }
}
