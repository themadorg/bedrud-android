package com.bedrud.app.testutil

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * The WCAG floor for icons and other graphics against what they are drawn on.
 */
const val NON_TEXT_CONTRAST = 3.0

/**
 * The WCAG AA floor for normal-size text against its background.
 */
const val TEXT_CONTRAST = 4.5

/**
 * WCAG contrast ratio between two opaque colors, always the lighter over the darker, from
 * Compose's own relative luminance.
 */
fun contrastRatio(first: Color, second: Color): Double {
    val lighter = maxOf(first.luminance(), second.luminance())
    val darker = minOf(first.luminance(), second.luminance())
    return ((lighter + 0.05f) / (darker + 0.05f)).toDouble()
}
