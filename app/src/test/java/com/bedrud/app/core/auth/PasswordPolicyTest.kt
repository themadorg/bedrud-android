package com.bedrud.app.core.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The one length rule sign-up, sign-in and changing a password all check, at its edge. */
class PasswordPolicyTest {

    @Test
    fun `should ask for twelve characters`() {
        assertEquals(12, PasswordPolicy.MIN_LENGTH)
    }

    @Test
    fun `should refuse a password one character short`() {
        assertFalse(PasswordPolicy.meetsMinLength("a".repeat(PasswordPolicy.MIN_LENGTH - 1)))
    }

    @Test
    fun `should accept a password of exactly the minimum length`() {
        assertTrue(PasswordPolicy.meetsMinLength("a".repeat(PasswordPolicy.MIN_LENGTH)))
    }

    @Test
    fun `should refuse an empty password`() {
        assertFalse(PasswordPolicy.meetsMinLength(""))
    }
}
