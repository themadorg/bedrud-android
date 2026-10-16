package com.bedrud.app.core.chat

import androidx.test.platform.app.InstrumentationRegistry
import com.bedrud.app.core.api.ApiHeaders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val ServerURL = "https://bedrud.example"
private const val AccessToken = "access-token"

/**
 * The request the chat draws a received image with. Its address comes from whoever sent the
 * message, so the image loader may carry the account's token only to the account's own server.
 */
class ChatImageUtilsTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun authorizationFor(url: String): String? =
        ChatImageUtils.imageRequest(context, ServerURL, url, AccessToken).headers[ApiHeaders.AUTHORIZATION]

    @Test
    fun shouldSendTokenWithImageOnOwnServer() {
        assertEquals(ApiHeaders.bearer(AccessToken), authorizationFor("/uploads/chat/room/picture.png"))
    }

    @Test
    fun shouldNotSendTokenWithImageOnAnotherServer() {
        assertNull(authorizationFor("https://elsewhere.example/picture.png"))
    }
}
