package com.bedrud.app.testutil

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap

/** The squared distance between two colours' red, green and blue channels. */
private fun Color.distanceTo(other: Color): Float {
    val red = this.red - other.red
    val green = this.green - other.green
    val blue = this.blue - other.blue
    return red * red + green * green + blue * blue
}

/**
 * How close, as a squared channel distance, a pixel must be to a paint to be that paint itself:
 * about eight steps of 255 per channel, room for rounding but not for a blended edge.
 */
private const val SamePaintDistance = 0.001f

/** Which of the known [paints] this pixel is nearest to: what, of everything drawn there, drew it. */
fun Color.nearestOf(paints: List<Color>): Color = paints.minBy { distanceTo(it) }

/**
 * Whether this pixel is [paint] itself rather than merely nearest to it. The anti-aliased edge of
 * grey text on a light surface can land nearer a pale fill than to either of its own colours.
 */
fun Color.isPaint(paint: Color): Boolean = distanceTo(paint) < SamePaintDistance

/**
 * The first and last pixel rows holding a pixel [isInk] accepts, looking only in [columns].
 *
 * What a reader sees as "where the letters are" is where their pixels are, which is what this
 * measures: rendered output, not the layout numbers that were meant to produce it.
 */
fun PixelMap.rowsWhere(columns: IntRange = 0 until width, isInk: (Color) -> Boolean): IntRange {
    val rows = (0 until height).filter { y -> columns.any { x -> isInk(this[x, y]) } }
    check(rows.isNotEmpty()) { "no pixel in columns $columns matched" }
    return rows.first()..rows.last()
}

/** The middle of a run of pixel rows, in pixels from the image's top. */
val IntRange.centre: Float
    get() = (first + last) / 2f
