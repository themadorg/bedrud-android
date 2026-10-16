package com.bedrud.app.core.pip

import org.junit.Assert.assertEquals
import org.junit.Test

/** The shape of the picture-in-picture window, and where it grows from. */
class PipWindowTest {

    @Test
    fun `should take the shape of a landscape video`() {
        assertEquals(PipAspect(1280, 720), pipAspect(videoWidth = 1280, videoHeight = 720))
    }

    @Test
    fun `should take the shape of a portrait video`() {
        assertEquals(PipAspect(720, 1280), pipAspect(videoWidth = 720, videoHeight = 1280))
    }

    /** An avatar and a name chip need no width to spare, so a voice-only window is square. */
    @Test
    fun `should be square when there is no video`() {
        assertEquals(PipAspect.Square, pipAspect(videoWidth = null, videoHeight = null))
    }

    @Test
    fun `should be square when the video has not reported its size yet`() {
        assertEquals(PipAspect.Square, pipAspect(videoWidth = 0, videoHeight = 0))
    }

    /** Android refuses a window narrower or wider than its limits, and throws for one asked of it. */
    @Test
    fun `should keep a very tall video within the narrowest window Android allows`() {
        assertEquals(PipAspect.Narrowest, pipAspect(videoWidth = 720, videoHeight = 2400))
    }

    @Test
    fun `should keep a very wide video within the widest window Android allows`() {
        assertEquals(PipAspect.Widest, pipAspect(videoWidth = 3000, videoHeight = 720))
    }

    /** The window grows from the part of the screen it will show, in its own shape. */
    @Test
    fun `should grow a square window from the middle of a tall tile`() {
        val tile = PipBounds(left = 0, top = 100, right = 400, bottom = 900)

        assertEquals(
            PipBounds(left = 0, top = 300, right = 400, bottom = 700),
            pipSourceRect(tile, PipAspect.Square),
        )
    }

    @Test
    fun `should grow a wide window from the middle of a square tile`() {
        val tile = PipBounds(left = 0, top = 0, right = 1600, bottom = 1600)

        assertEquals(
            PipBounds(left = 0, top = 350, right = 1600, bottom = 1250),
            pipSourceRect(tile, PipAspect(16, 9)),
        )
    }
}
