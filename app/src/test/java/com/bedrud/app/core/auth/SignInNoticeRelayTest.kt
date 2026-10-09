package com.bedrud.app.core.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A notice as Settings words it when it signs the user out. */
private const val NOTICE = "Your password was changed. Sign in with your new password."

class SignInNoticeRelayTest {

    @Test
    fun `message holds nothing until a notice is reported`() {
        assertNull(SignInNoticeRelay().message.value)
    }

    @Test
    fun `message holds a reported notice until it is consumed`() {
        val relay = SignInNoticeRelay()

        relay.report(NOTICE)
        assertEquals(NOTICE, relay.message.value)

        relay.consume()
        assertNull(relay.message.value)
    }
}
