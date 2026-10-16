package com.bedrud.app.core.chat

import com.bedrud.app.core.api.ApiHeaders
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/** A server the account is not on; nothing is ever sent to it, since the URL under test is absolute. */
private const val OtherServerURL = "https://bedrud.example"

private const val AccessToken = "access-token"

/**
 * What [ChatImageSaver] sends when it fetches a received image. The address of a chat image comes
 * from whoever sent the message, so the account's token may go only to the account's own server.
 *
 * Writing the picture to the gallery needs `MediaStore`, which a local unit test has no
 * implementation of, so that step fails here; the request made before it is what is checked.
 */
class ChatImageSaverTest {

    private lateinit var server: MockWebServer

    @Before
    fun startServer() {
        server = MockWebServer()
        server.start()
        server.enqueue(MockResponse().setBody("image").setHeader("Content-Type", "image/png"))
    }

    @After
    fun stopServer() {
        server.shutdown()
    }

    /** Saves [url] as a picture sent by someone in a room on [serverURL], and returns the fetch it made. */
    private fun fetchMadeForSaving(url: String, serverURL: String): RecordedRequest {
        val saver = ChatImageSaver(contentResolver = mockk(relaxed = true))
        runCatching { runBlocking { saver.save(url, serverURL, AccessToken) } }
        return checkNotNull(server.takeRequest(RequestTimeoutSeconds, TimeUnit.SECONDS)) { "no request made" }
    }

    @Test
    fun `should send the token with an image on the account's own server`() {
        val request = fetchMadeForSaving(url = "/uploads/chat/room/picture.png", serverURL = server.url("/").toString())

        assertEquals(ApiHeaders.bearer(AccessToken), request.getHeader(ApiHeaders.AUTHORIZATION))
    }

    @Test
    fun `should not send the token with an image on another server`() {
        val request = fetchMadeForSaving(url = server.url("/picture.png").toString(), serverURL = OtherServerURL)

        assertNull(request.getHeader(ApiHeaders.AUTHORIZATION))
    }

    private companion object {
        const val RequestTimeoutSeconds = 5L
    }
}
