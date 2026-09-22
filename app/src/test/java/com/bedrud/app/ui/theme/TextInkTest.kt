package com.bedrud.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class TextInkTest {

    @Test
    fun `inkCenteringOffsetPx returns zero when the ink already sits centred in the box`() {
        // Baseline 75 into a 100-tall box, ink reaching 50 above it: the ink's centre is 50, which
        // is the box's centre. A font like this needs no correction, and must not be given one.
        assertEquals(
            0f,
            inkCenteringOffsetPx(
                boxHeightPx = 100f,
                firstBaselinePx = 75f,
                inkTopPx = -50,
                inkBottomPx = 0,
            ),
            TOLERANCE,
        )
    }

    @Test
    fun `inkCenteringOffsetPx reproduces Vazirmatn's 0_156em error for a capital`() {
        // Vazirmatn's own metrics at a 100px em: ascender 1.0254em, descender 0.5371em, so the box
        // is 156.25 tall with the baseline 102.54 down it. A capital's ink is capHeight 0.7998em
        // above the baseline and nothing below. The ascent reserves room for Arabic marks a Latin
        // capital never uses, which is what pushes the letter above the box's centre.
        assertEquals(
            15.585f,
            inkCenteringOffsetPx(
                boxHeightPx = 156.25f,
                firstBaselinePx = 102.54f,
                inkTopPx = -80,
                inkBottomPx = 0,
            ),
            TOLERANCE,
        )
    }

    @Test
    fun `inkCenteringOffsetPx reproduces Roboto's near-zero error for the same capital`() {
        // Roboto at the same 100px em: ascender 0.9277em, descender 0.2441em, capHeight 0.7109em.
        // The 1.3px that comes out is 0.013em — the fraction of a pixel that makes "centre the
        // layout box" the usual advice everywhere else.
        assertEquals(
            1.325f,
            inkCenteringOffsetPx(
                boxHeightPx = 117.18f,
                firstBaselinePx = 92.77f,
                inkTopPx = -71,
                inkBottomPx = 0,
            ),
            TOLERANCE,
        )
    }

    @Test
    fun `inkCenteringOffsetPx moves the text up when the box reserves more room below the ink`() {
        // The mirror of the Vazirmatn case: a box whose descent is the generous half. The
        // correction has to be able to go negative, or a font like this is left worse than
        // uncorrected.
        assertEquals(
            -30f,
            inkCenteringOffsetPx(
                boxHeightPx = 100f,
                firstBaselinePx = 90f,
                inkTopPx = -20,
                inkBottomPx = 0,
            ),
            TOLERANCE,
        )
    }

    @Test
    fun `inkCenteringOffsetPx measures ink that descends below the baseline`() {
        // A lowercase "g" or a Persian tail puts ink under the baseline, which carries the ink's
        // own centre down with it. In Vazirmatn's box from the case above, 20px of descender asks
        // for 10px less correction than the bare capital's 15.585 — half the descent, because a
        // centre moves half as far as the edge that moved it.
        assertEquals(
            5.585f,
            inkCenteringOffsetPx(
                boxHeightPx = 156.25f,
                firstBaselinePx = 102.54f,
                inkTopPx = -80,
                inkBottomPx = 20,
            ),
            TOLERANCE,
        )
    }

    @Test
    fun `inkCenteringOffsetPx scales with the size the text is drawn at`() {
        // Twice the em, twice the correction. This is why the offset is measured at the style it
        // will be rendered with rather than written down once: a constant would be right for one
        // type scale, one density and one reader font-size setting.
        val single = inkCenteringOffsetPx(
            boxHeightPx = 156.25f,
            firstBaselinePx = 102.54f,
            inkTopPx = -80,
            inkBottomPx = 0,
        )
        val double = inkCenteringOffsetPx(
            boxHeightPx = 312.5f,
            firstBaselinePx = 205.08f,
            inkTopPx = -160,
            inkBottomPx = 0,
        )
        assertEquals(single * 2f, double, TOLERANCE)
    }

    private companion object {
        /** Sub-pixel slack: these are float pixel positions, not exact decimals. */
        const val TOLERANCE = 0.01f
    }
}
