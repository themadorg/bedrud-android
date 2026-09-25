package com.bedrud.app.ui.screens.meeting

import androidx.compose.ui.graphics.Color
import com.bedrud.app.testutil.NON_TEXT_CONTRAST
import com.bedrud.app.testutil.contrastRatio
import com.bedrud.app.ui.theme.DarkColorScheme
import com.bedrud.app.ui.theme.LightColorScheme
import org.junit.Assert.assertTrue
import org.junit.Test

class MeetingChromeTest {

    private val schemes = listOf("light" to LightColorScheme, "dark" to DarkColorScheme)

    private fun assertReadable(label: String, content: Color, container: Color) {
        val ratio = contrastRatio(content, container)
        assertTrue(
            "$label is ${"%.2f".format(ratio)}:1, below $NON_TEXT_CONTRAST:1",
            ratio >= NON_TEXT_CONTRAST,
        )
    }

    @Test
    fun `meetingChromeColors keeps a lit button's icon readable on its fill`() {
        // Screen share, chat and a transmitting push-to-talk pill fill with buttonActive. Their icon
        // was drawn in onButton, the colour for the unlit fill, which nearly vanished in dark theme.
        for ((name, scheme) in schemes) {
            val colors = meetingChromeColors(scheme)
            assertReadable("$name onButtonActive on buttonActive", colors.onButtonActive, colors.buttonActive)
        }
    }

    @Test
    fun `meetingChromeColors keeps every other icon readable on its fill`() {
        for ((name, scheme) in schemes) {
            val colors = meetingChromeColors(scheme)
            assertReadable("$name onButton on button", colors.onButton, colors.button)
            assertReadable("$name onButtonMediaOff on buttonMediaOff", colors.onButtonMediaOff, colors.buttonMediaOff)
            assertReadable("$name onAccent on accent", colors.onAccent, colors.accent)
            assertReadable("$name onMediaError on mediaError", colors.onMediaError, colors.mediaError)
            assertReadable("$name onEndCall on endCall", colors.onEndCall, colors.endCall)
        }
    }
}
