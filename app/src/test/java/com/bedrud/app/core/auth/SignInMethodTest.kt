package com.bedrud.app.core.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignInMethodTest {

    @Test
    fun `signInMethodOf reads a missing provider as email`() {
        assertEquals(SignInMethod.Email, signInMethodOf(null))
    }

    @Test
    fun `signInMethodOf reads the server's local provider as email`() {
        // The server calls an email-and-password account "local", which is not a word to show
        // anyone, least of all untranslated.
        assertEquals(SignInMethod.Email, signInMethodOf("local"))
    }

    @Test
    fun `signInMethodOf reads a passkey account as passkey`() {
        assertEquals(SignInMethod.Passkey, signInMethodOf("passkey"))
    }

    @Test
    fun `signInMethodOf reads a guest account as guest, not as an identity provider`() {
        // An identity provider is shown by its own name, which would put the server's English
        // word "Guest" on screen in every language.
        assertEquals(SignInMethod.Guest, signInMethodOf("guest"))
    }

    @Test
    fun `signInMethodOf keeps an identity provider's own name`() {
        assertEquals(SignInMethod.Provider("Google"), signInMethodOf("google"))
    }

    @Test
    fun `hasPassword is true only for accounts the app holds a password for`() {
        assertTrue(signInMethodOf(null).hasPassword)
        assertTrue(signInMethodOf("local").hasPassword)
        assertTrue(signInMethodOf("passkey").hasPassword)
        assertFalse(signInMethodOf("guest").hasPassword)
        assertFalse(signInMethodOf("google").hasPassword)
    }
}
