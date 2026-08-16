package com.bedrud.app.core.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Only the string handling is covered here — recognising an inlined image, reading its declared
 * media type, and refusing anything that is not one. Actually turning the payload into bytes or a
 * bitmap goes through `android.util.Base64` and `BitmapFactory`, neither of which a local unit test
 * has an implementation of; that path is covered by saving a real picture on a device.
 */
class ChatImageDecoderTest {

    @Test
    fun `recognises an inlined image`() {
        assertTrue(ChatImageDecoder.isInlineImage("data:image/png;base64,iVBORw0KGgo="))
        assertTrue(ChatImageDecoder.isInlineImage("data:image/jpeg;base64,/9j/4AAQ"))
    }

    @Test
    fun `treats a served path or url as not inlined`() {
        assertFalse(ChatImageDecoder.isInlineImage("/uploads/chat/room-1/abc.jpg"))
        assertFalse(ChatImageDecoder.isInlineImage("https://example.test/a.png"))
        assertFalse(ChatImageDecoder.isInlineImage(""))
    }

    @Test
    fun `reads the media type from an inlined image's own header`() {
        assertEquals("image/png", ChatImageDecoder.inlineMimeType("data:image/png;base64,iVBORw0KGgo="))
        assertEquals("image/jpeg", ChatImageDecoder.inlineMimeType("data:image/jpeg;base64,/9j/4AAQ"))
    }

    @Test
    fun `has no media type for a served url`() {
        assertNull(ChatImageDecoder.inlineMimeType("https://example.test/a.png"))
        assertNull(ChatImageDecoder.inlineMimeType("data:;base64,AAAA"))
    }

    @Test
    fun `has no bytes for a served url`() {
        assertNull(ChatImageDecoder.decodeInlineBytes("https://example.test/a.png"))
    }

    @Test
    fun `refuses to decode anything that is not inlined`() {
        // The URL forms above belong to the image loader; handing one here must not half-work.
        assertTrue(ChatImageDecoder.decodeInlineImage("https://example.test/a.png") == null)
        assertTrue(ChatImageDecoder.decodeInlineImage("/uploads/chat/a.png") == null)
    }
}
