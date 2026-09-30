package com.bedrud.app.ui.screens.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.bedrud.app.R
import com.bedrud.app.core.auth.SignInMethod
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

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
}
