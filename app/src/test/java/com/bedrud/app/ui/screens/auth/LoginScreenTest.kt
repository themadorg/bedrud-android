package com.bedrud.app.ui.screens.auth

import android.content.Context
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.bedrud.app.R
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.UnknownHostException

/**
 * The sign-in hub's subtitle, which offers only the ways in the server allows, and what it says when
 * passkey sign-in fails. Strings come back as their resource id, so no test depends on a translation.
 */
class LoginScreenTest {

    private val context = mockk<Context> {
        every { getString(any()) } answers { "string:${firstArg<Int>()}" }
    }

    private fun string(id: Int) = "string:$id"

    @Test
    fun `should offer continuing as a guest when the server allows it`() {
        assertEquals(R.string.auth_subtitle_hubChoose, hubSubtitle(guestAllowed = true))
    }

    @Test
    fun `should only ask to sign in when the server turns guest sign-in off`() {
        assertEquals(R.string.auth_subtitle_hubSignIn, hubSubtitle(guestAllowed = false))
    }

    /** Closing the passkey picker is the reader's own choice, not a failure to report. */
    @Test
    fun `should say nothing when the reader closes the passkey picker`() {
        assertNull(passkeyFailureMessage(GetCredentialCancellationException("activity is cancelled by the user."), context))
    }

    @Test
    fun `should name a lost connection when passkey sign-in cannot reach the server`() {
        assertEquals(
            string(R.string.error_network_unavailable),
            passkeyFailureMessage(UnknownHostException("Unable to resolve host \"bedrud.example\""), context)
        )
    }

    /** The passkey flow words its own failures in English, with a status code, for the log. */
    @Test
    fun `should report any other passkey failure in the app's words, never by its own text`() {
        assertEquals(
            string(R.string.error_generic),
            passkeyFailureMessage(IllegalStateException("passkey login verification failed: 401"), context)
        )
    }
}
