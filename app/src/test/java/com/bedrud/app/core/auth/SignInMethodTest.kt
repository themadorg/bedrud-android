package com.bedrud.app.core.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When a user's password last changed, as the server writes the time. */
private const val PASSWORD_CHANGED_AT = "2026-09-01T10:00:00Z"

class SignInMethodTest {

    @Test
    fun `signInMethodOf reads a missing provider as email`() {
        assertEquals(SignInMethod.Email, signInMethodOf(null, passwordChangedAt = null))
    }

    @Test
    fun `signInMethodOf reads the server's local provider as email`() {
        // The server calls an email-and-password account "local", which is not a word to show
        // anyone, least of all untranslated.
        assertEquals(SignInMethod.Email, signInMethodOf("local", passwordChangedAt = null))
    }

    @Test
    fun `signInMethodOf reads a passkey account that has set a password as having one`() {
        assertEquals(
            SignInMethod.Passkey(hasPassword = true),
            signInMethodOf("passkey", PASSWORD_CHANGED_AT)
        )
    }

    @Test
    fun `signInMethodOf reads a passkey account that never set a password as having none`() {
        // An account made with a passkey starts without a password, and the server stamps the
        // time on every password it sets, so no stamp means none was ever set.
        assertEquals(
            SignInMethod.Passkey(hasPassword = false),
            signInMethodOf("passkey", passwordChangedAt = null)
        )
    }

    @Test
    fun `signInMethodOf reads a guest account as guest, not as an identity provider`() {
        // An identity provider is shown by its own name, which would put the server's English
        // word "Guest" on screen in every language.
        assertEquals(SignInMethod.Guest, signInMethodOf("guest", passwordChangedAt = null))
    }

    @Test
    fun `signInMethodOf keeps an identity provider's own name`() {
        assertEquals(SignInMethod.Provider("Google"), signInMethodOf("google", passwordChangedAt = null))
    }

    @Test
    fun `hasPassword is true for an email account the server never stamped`() {
        // Signing up with email and password leaves the stamp empty until the first change, so
        // for an email account its absence says nothing.
        assertTrue(signInMethodOf("local", passwordChangedAt = null).hasPassword)
        assertTrue(signInMethodOf(null, passwordChangedAt = null).hasPassword)
    }

    @Test
    fun `canHavePassword is true only for accounts that can sign in with a password`() {
        assertTrue(signInMethodOf(null, passwordChangedAt = null).canHavePassword)
        assertTrue(signInMethodOf("local", passwordChangedAt = null).canHavePassword)
        assertTrue(signInMethodOf("passkey", passwordChangedAt = null).canHavePassword)
        assertTrue(signInMethodOf("passkey", PASSWORD_CHANGED_AT).canHavePassword)
        assertFalse(signInMethodOf("guest", passwordChangedAt = null).canHavePassword)
        assertFalse(signInMethodOf("google", passwordChangedAt = null).canHavePassword)
    }

    @Test
    fun `hasPassword is false for accounts that cannot have one`() {
        assertFalse(signInMethodOf("guest", passwordChangedAt = null).hasPassword)
        assertFalse(signInMethodOf("google", PASSWORD_CHANGED_AT).hasPassword)
    }
}
