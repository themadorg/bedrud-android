package com.bedrud.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * The floor WCAG AA sets for normal-size text against its background.
 */
private const val CONTRAST_AA = 4.5

/**
 * CIE76 distance below which two colors read as the same color rather than as two.
 *
 * Rose and red share a hue family, so lightness carries most of this distance — the light scheme
 * clears it by a narrow margin, and any future move of either role needs re-measuring rather than
 * eyeballing.
 */
private const val DISTINCT_ROLES = 20.0

/**
 * Undoes the sRGB transfer function, giving the linear intensity the contrast and Lab formulas
 * both expect.
 */
private fun linearize(channel: Float): Double {
    val value = channel.toDouble()
    return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
}

/**
 * Relative luminance as WCAG defines it.
 */
private fun luminance(color: Color): Double =
    0.2126 * linearize(color.red) + 0.7152 * linearize(color.green) + 0.0722 * linearize(color.blue)

/**
 * WCAG contrast ratio between two colors, always expressed as the lighter over the darker.
 */
private fun contrastRatio(first: Color, second: Color): Double {
    val lighter = maxOf(luminance(first), luminance(second))
    val darker = minOf(luminance(first), luminance(second))
    return (lighter + 0.05) / (darker + 0.05)
}

/**
 * Converts a color to CIE L*a*b* under a D65 white point, so distances between two colors
 * approximate how far apart they look rather than how far apart their channel values are.
 */
private fun toLab(color: Color): Triple<Double, Double, Double> {
    val red = linearize(color.red)
    val green = linearize(color.green)
    val blue = linearize(color.blue)
    val x = (0.4124 * red + 0.3576 * green + 0.1805 * blue) / 0.95047
    val y = 0.2126 * red + 0.7152 * green + 0.0722 * blue
    val z = (0.0193 * red + 0.1192 * green + 0.9505 * blue) / 1.08883
    val pivot = { value: Double ->
        if (value > 0.008856) value.pow(1.0 / 3.0) else 7.787 * value + 16.0 / 116.0
    }
    val fx = pivot(x)
    val fy = pivot(y)
    val fz = pivot(z)
    return Triple(116.0 * fy - 16.0, 500.0 * (fx - fy), 200.0 * (fy - fz))
}

/**
 * CIE76 perceptual distance between two colors.
 */
private fun perceptualDistance(first: Color, second: Color): Double {
    val (firstL, firstA, firstB) = toLab(first)
    val (secondL, secondA, secondB) = toLab(second)
    return ((firstL - secondL).pow(2) + (firstA - secondA).pow(2) + (secondB - firstB).pow(2))
        .pow(0.5)
}

class ThemeTest {

    private val schemes = listOf("light" to LightColorScheme, "dark" to DarkColorScheme)

    /**
     * The error role has to say "something failed" on its own. Rose is the brand primary and red
     * is the error, so the two sit in the same hue family and nothing but distance keeps a
     * selected control from reading as a broken one.
     */
    @Test
    fun `should keep the error role perceptually clear of the primary role`() {
        schemes.forEach { (name: String, scheme: ColorScheme) ->
            val distance = perceptualDistance(scheme.error, scheme.primary)
            assertTrue(
                "$name: error and primary are $distance apart, under the $DISTINCT_ROLES needed " +
                    "for them to read as two different colors",
                distance >= DISTINCT_ROLES
            )
        }
    }

    /**
     * Error text is drawn straight onto the page background, not onto an error container.
     */
    @Test
    fun `should keep error text legible on the background it is drawn on`() {
        schemes.forEach { (name: String, scheme: ColorScheme) ->
            val ratio = contrastRatio(scheme.error, scheme.background)
            assertTrue(
                "$name: error on background is $ratio, under the AA floor of $CONTRAST_AA",
                ratio >= CONTRAST_AA
            )
        }
    }

    /**
     * Guards the other half of moving the error role: a destructive button fills with `error` and
     * labels itself with `onError`, so darkening one without the other silently drops that label
     * below AA.
     */
    @Test
    fun `should keep the error label legible on an error fill`() {
        schemes.forEach { (name: String, scheme: ColorScheme) ->
            val ratio = contrastRatio(scheme.onError, scheme.error)
            assertTrue(
                "$name: onError on error is $ratio, under the AA floor of $CONTRAST_AA",
                ratio >= CONTRAST_AA
            )
        }
    }
}
