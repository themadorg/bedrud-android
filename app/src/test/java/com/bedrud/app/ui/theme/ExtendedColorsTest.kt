package com.bedrud.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The extended colors Material 3 has no role for. The recording dot is red because "recording" is
 * red by convention, not because anything failed, so it has its own color rather than borrowing
 * the error role: a later move of the error red must not move the dot with it.
 */
class ExtendedColorsTest {

    @Test
    fun `should draw the recording dot in the light theme's red`() {
        assertEquals(Red700, LightExtendedColors.recording)
    }

    @Test
    fun `should draw the recording dot in the dark theme's red`() {
        assertEquals(Red500, DarkExtendedColors.recording)
    }
}
