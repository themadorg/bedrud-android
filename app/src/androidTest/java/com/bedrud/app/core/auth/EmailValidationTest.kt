package com.bedrud.app.core.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one email shape check sign-in, sign-up and password recovery share. Instrumented, because it
 * is Android's own pattern, which a local unit test has no implementation of.
 */
class EmailValidationTest {

    @Test
    fun shouldAcceptAPlainAddress() {
        assertTrue(isValidEmail("someone@example.com"))
    }

    @Test
    fun shouldAcceptAnAddressWithATagAndASubdomain() {
        assertTrue(isValidEmail("someone+rooms@mail.example.co.uk"))
    }

    /** Every caller submits the trimmed address, so a pasted one with spaces around it is fine. */
    @Test
    fun shouldAcceptAnAddressWithSpacesAroundIt() {
        assertTrue(isValidEmail("  someone@example.com \n"))
    }

    @Test
    fun shouldRefuseAnAddressWithoutAnAt() {
        assertFalse(isValidEmail("someone.example.com"))
    }

    @Test
    fun shouldRefuseAnAddressWithoutADomain() {
        assertFalse(isValidEmail("someone@"))
    }

    @Test
    fun shouldRefuseAnAddressWithASpaceInside() {
        assertFalse(isValidEmail("some one@example.com"))
    }

    @Test
    fun shouldRefuseAnEmptyAddress() {
        assertFalse(isValidEmail("   "))
    }
}
