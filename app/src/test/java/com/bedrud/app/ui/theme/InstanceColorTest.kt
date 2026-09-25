package com.bedrud.app.ui.theme

import com.bedrud.app.models.InstanceColorPalette
import com.bedrud.app.testutil.TEXT_CONTRAST
import com.bedrud.app.testutil.contrastRatio
import org.junit.Assert.assertTrue
import org.junit.Test

class InstanceColorTest {

    @Test
    fun `OnInstanceColor keeps an initial readable on every server color`() {
        // The fallback is included: a server whose color is missing or malformed shows it.
        val serverColors = InstanceColorPalette.map(::parseInstanceColor) + parseInstanceColor(null)
        for (serverColor in serverColors) {
            val ratio = contrastRatio(OnInstanceColor, serverColor)
            assertTrue(
                "initial on $serverColor is ${"%.2f".format(ratio)}:1, below $TEXT_CONTRAST:1",
                ratio >= TEXT_CONTRAST,
            )
        }
    }
}
