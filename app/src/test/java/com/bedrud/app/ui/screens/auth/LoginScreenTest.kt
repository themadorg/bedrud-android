package com.bedrud.app.ui.screens.auth

import com.bedrud.app.R
import org.junit.Assert.assertEquals
import org.junit.Test

/** The sign-in hub's subtitle, which offers only the ways in the server allows. */
class LoginScreenTest {

    @Test
    fun `should offer continuing as a guest when the server allows it`() {
        assertEquals(R.string.auth_subtitle_hubChoose, hubSubtitle(guestAllowed = true))
    }

    @Test
    fun `should only ask to sign in when the server turns guest sign-in off`() {
        assertEquals(R.string.auth_subtitle_hubSignIn, hubSubtitle(guestAllowed = false))
    }
}
