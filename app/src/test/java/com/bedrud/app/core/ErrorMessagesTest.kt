package com.bedrud.app.core

import android.content.Context
import com.bedrud.app.R
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

/**
 * Which message a failure is reported with. The message is chosen by the failure's type, never
 * taken from its own text, which is English and technical. Strings come back as their resource id,
 * so no test depends on a translation.
 */
class ErrorMessagesTest {

    private val context = mockk<Context> {
        every { getString(any()) } answers { "string:${firstArg<Int>()}" }
    }

    private fun messageFor(failure: Throwable) = failure.toUserMessage(context)

    private fun string(id: Int) = "string:$id"

    @Test
    fun `should report an unresolvable host as no network`() {
        assertEquals(string(R.string.error_network_unavailable), messageFor(UnknownHostException("bedrud.example")))
    }

    @Test
    fun `should report a timeout as a timeout`() {
        assertEquals(string(R.string.error_timeout), messageFor(SocketTimeoutException("timeout")))
        assertEquals(string(R.string.error_timeout), messageFor(InterruptedIOException("interrupted")))
    }

    @Test
    fun `should report a refused connection or a failed handshake as the server unreachable`() {
        assertEquals(string(R.string.error_server_unreachable), messageFor(ConnectException("refused")))
        assertEquals(string(R.string.error_server_unreachable), messageFor(SSLHandshakeException("handshake")))
    }

    @Test
    fun `should report anything else generically, never by its own text`() {
        assertEquals(string(R.string.error_generic), messageFor(IOException("Unexpected end of stream")))
        assertEquals(string(R.string.error_generic), messageFor(IllegalStateException("boom")))
    }
}
