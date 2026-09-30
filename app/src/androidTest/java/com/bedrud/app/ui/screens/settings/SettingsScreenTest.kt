package com.bedrud.app.ui.screens.settings

import android.app.Application
import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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

/** The server the guest is signed in to; `.invalid` never resolves, so nothing is fetched. */
private const val GUEST_INSTANCE_ID = "settings-screen-test-guest"
private const val GUEST_SERVER_URL = "https://example.invalid/"
private const val GUEST_SERVER_NAME = "Example"

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

    /** An [InstanceManager] whose only server has a guest signed in. */
    private fun signedInGuest(): InstanceManager {
        val instances = InstanceStore(context.getSharedPreferences(TEST_INSTANCES_PREFS, Context.MODE_PRIVATE))
        instances.addInstance(Instance(id = GUEST_INSTANCE_ID, serverURL = GUEST_SERVER_URL, displayName = GUEST_SERVER_NAME))
        AuthManager(context, GUEST_INSTANCE_ID).saveUser(
            User(id = GUEST_INSTANCE_ID, email = "", name = GUEST_SERVER_NAME, provider = "guest")
        )
        return InstanceManager(context.applicationContext as Application, instances, SettingsStore(context))
    }

    /** The width of the card whose section header reads [header]. */
    private fun cardWidth(@StringRes header: Int): Float {
        val headerNode = compose.onNodeWithText(context.getString(header), useUnmergedTree = true).fetchSemanticsNode()
        return checkNotNull(headerNode.parent).boundsInRoot.width
    }

    @After
    fun forgetGuest() {
        AuthManager(context, GUEST_INSTANCE_ID).logout()
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
        val instanceManager = signedInGuest()
        compose.setThemedContentAt(FontScales.Default) {
            SettingsContent(settingsStore = SettingsStore(context), instanceManager = instanceManager)
        }

        assertEquals(
            cardWidth(R.string.settings_section_appearance),
            cardWidth(R.string.settings_section_security),
            WIDTH_TOLERANCE_PX
        )
    }
}
