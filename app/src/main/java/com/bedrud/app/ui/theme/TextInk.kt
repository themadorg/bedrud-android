package com.bedrud.app.ui.theme

import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How far down the ink has to move for its own centre, rather than its box's, to sit at [boxHeightPx] / 2.
 *
 * All four measurements are pixels in the same layout: [boxHeightPx] and [firstBaselinePx] from the
 * top of the line, [inkTopPx] and [inkBottomPx] from the baseline, running negative upwards. The
 * baseline is what puts the two origins on one scale.
 *
 * Positive moves the text down. It must be able to go negative too: a font whose descent is the
 * generous half needs the opposite correction, and clamping at zero would leave it worse than
 * uncorrected.
 */
internal fun inkCenteringOffsetPx(
    boxHeightPx: Float,
    firstBaselinePx: Float,
    inkTopPx: Int,
    inkBottomPx: Int,
): Float {
    val inkCenterFromTop = firstBaselinePx + (inkTopPx + inkBottomPx) / 2f
    return boxHeightPx / 2f - inkCenterFromTop
}

/**
 * How far down a block of stacked lines has to move for its letters, from the first line's
 * capitals to the last line's baseline, to sit centred in the block's box.
 *
 * Only the two outer gaps decide it: the room between the block's top and its first capital, and
 * the room between its last baseline and its bottom. Whatever lies between — more lines, spacing —
 * sits inside both the box and the letters and moves their centres together, so it cancels out.
 *
 * [firstBaselinePx] and [firstInkTopPx] belong to the first line, measured from its own box top and
 * baseline; [lastBoxHeightPx] and [lastBaselinePx] belong to the last line, in its own box. A block
 * of one line reduces to [inkCenteringOffsetPx] for a capital.
 */
internal fun blockCenteringOffsetPx(
    firstBaselinePx: Float,
    firstInkTopPx: Int,
    lastBoxHeightPx: Float,
    lastBaselinePx: Float,
): Float {
    val roomAboveLetters = firstBaselinePx + firstInkTopPx
    val roomBelowLetters = lastBoxHeightPx - lastBaselinePx
    return (roomBelowLetters - roomAboveLetters) / 2f
}

/** A [Paint] carrying the same typeface and size Compose will render [style] with. */
@Composable
private fun rememberFontPaint(style: TextStyle): Paint {
    val resolver = LocalFontFamilyResolver.current
    val density = LocalDensity.current
    val typeface = resolver.resolve(
        fontFamily = style.fontFamily ?: FontFamily.Default,
        fontWeight = style.fontWeight ?: FontWeight.Normal,
        fontStyle = style.fontStyle ?: FontStyle.Normal,
        fontSynthesis = style.fontSynthesis ?: FontSynthesis.All,
    ).value as Typeface
    val sizePx = with(density) { style.fontSize.toPx() }
    return remember(typeface, sizePx) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = sizePx
        }
    }
}

/**
 * How far to move [text] down so its letters, rather than its font's box, sit centred.
 *
 * Centring a `Text` centres the box the font asks for — ascent above the baseline, descent below —
 * and that box is only a good stand-in for the letters when the two happen to line up. For Roboto
 * they nearly do: its box centre and the centre of a capital differ by 0.014em, a fraction of a
 * pixel at any UI size. That near-miss is why centring the layout box is the usual advice, and why
 * it is usually right.
 *
 * Vazirmatn, which this app uses so Persian and Arabic get a UI sans rather than a naskh, does not
 * line up. Its ascent reserves room for Arabic marks and its descent for Persian tails, and neither
 * is symmetric about the letters between them:
 *
 * ```
 *              ascender   descender   capHeight   box centre vs cap centre
 *   Vazirmatn   1.0254em    0.5371em    0.7998em            0.156em
 *   Roboto      0.9277em    0.2441em    0.7109em            0.014em
 * ```
 *
 * 0.156em is eleven times Roboto's error — around 5px on a 12sp label at 2.6x — which is why
 * uppercase in a circle, and short text in a tight container, read as sitting high throughout the
 * app rather than in any one component.
 *
 * The correction is measured from the font that is about to be used, at the size it will be used
 * at, so it follows the typeface, the type scale, the display density and the reader's font-size
 * setting on its own. A number written down instead would be right for exactly one combination of
 * those, which is what makes it drift the moment any of them changes.
 *
 * Returns zero for a font whose box already agrees with its letters, so applying it is never a
 * change for its own sake — it does nothing where nothing is wrong.
 *
 * Prefer [inkCentered] at a call site; this is for the few places that need the number itself.
 */
@Composable
fun rememberInkCenteringOffset(text: String, style: TextStyle): Dp {
    val measurer = rememberTextMeasurer()
    val paint = rememberFontPaint(style)
    val density = LocalDensity.current
    return remember(measurer, paint, text, style, density) {
        if (text.isEmpty()) return@remember 0.dp
        val ink = Rect().also { paint.getTextBounds(text, 0, text.length, it) }
        if (ink.isEmpty) return@remember 0.dp
        // The box has to come from the layout rather than from the typeface, because a line takes
        // its height from every font that ends up on it. Vazirmatn carries no Cyrillic, Greek or
        // CJK, so those arrive from the platform's fallback — and where that fallback is the taller
        // of the two, as Noto Sans CJK is, it is the fallback that sets the box. Asking the
        // typeface alone put an ideograph 6px low in a 147px circle.
        val layout = measurer.measure(text, style)
        val offsetPx = inkCenteringOffsetPx(
            boxHeightPx = layout.size.height.toFloat(),
            firstBaselinePx = layout.firstBaseline,
            inkTopPx = ink.top,
            inkBottomPx = ink.bottom,
        )
        with(density) { offsetPx.toDp() }
    }
}

/**
 * Moves a `Text` drawing [text] at [style] so that string's own letters sit centred.
 *
 * For **one glyph centred in a shape** — an avatar's initial, a reaction's emoji. There is nothing
 * beside it to line up with, the glyph the caller passes is the whole of what has to look centred,
 * and its ink may be nothing like a capital's: an emoji fills the em box, and a CJK ideograph
 * arrives from a fallback font whose line box is taller than Vazirmatn's. Measuring the glyph is
 * the only thing that covers all of those.
 *
 * Not for text that sits beside more text. The correction depends on the string: a word with a
 * descender has its ink centre lower and so asks for less of one, and two labels in a row then stop
 * sharing a baseline — measured at 4px apart across the bottom navigation, with "Settings" visibly
 * riding above "Rooms". Use [typeCentered] there.
 *
 * Not for a paragraph centred in a large empty area either: there is nothing for the eye to compare
 * the letters against, and moving them costs a line of code to buy nothing.
 */
@Composable
fun Modifier.inkCentered(text: String, style: TextStyle): Modifier =
    offset(y = rememberInkCenteringOffset(text, style))

/**
 * The glyph whose ink stands in for "where the letters are" at a given [TextStyle].
 *
 * A capital H: flat top, flat bottom, no overshoot, and no descender, so its ink is the cap box
 * itself. Which glyph it is matters far less than that it is always the same one — every string in
 * a style has to be moved by the same amount, or the strings stop agreeing with each other.
 */
private const val CAP_HEIGHT_REFERENCE = "H"

/**
 * How far to move text at [style] down so the type's letters, rather than its font's box, sit centred.
 *
 * The same correction [rememberInkCenteringOffset] measures, taken once against a fixed reference
 * glyph instead of against the string being drawn. That makes it a property of the text style
 * alone: every label sharing a style is moved by the same amount and so keeps the baseline it
 * shared before, which is what the per-string form cannot promise.
 *
 * Two costs come with that, both deliberate:
 *
 * - The reference is a Latin capital, so a Persian or Arabic run is moved by a number derived from
 *   a measure its own script has no equivalent of. It is still the right trade: within a row every
 *   label moves together, and a row that agrees with itself beats a row where each word is
 *   individually ideal.
 * - A string whose line box comes from a taller fallback font — Noto Sans CJK, where Vazirmatn
 *   carries no glyphs — is centred in a box this correction did not measure, so it lands slightly
 *   off. Labels in one row are near-always one script, which is where this is used.
 */
@Composable
fun rememberTypeCenteringOffset(style: TextStyle): Dp =
    rememberInkCenteringOffset(CAP_HEIGHT_REFERENCE, style)

/**
 * Moves a `Text` at [style] so the type's letters sit centred, by the same amount for every string.
 *
 * For **text centred against something that is not text** — a button label in its fixed-height
 * container, a navigation label under its icon, a chip's or a badge's label, a list item's line
 * beside its switch, a hint in a field beside an icon, any label paired with an icon. Peer labels
 * keep a shared baseline because the correction does not depend on which word each one happens to
 * be. For two or more lines centred as one, use the block form, which takes the first and last
 * line's styles.
 *
 * Apply it to **every** such site, not the convenient ones. The correction's whole risk is
 * partiality: a corrected `BedrudButton` label measured 5px below the plain `TextButton` beside it
 * in the same dialog, two controls that had agreed with each other until one was improved. The type
 * scale cannot carry this instead — a blanket shift there moves ink inside a box each container has
 * already placed, which over-corrected the dashboard's search field while half-correcting the
 * navigation.
 *
 * For a single glyph centred in a shape, [inkCentered] measures that glyph instead.
 */
@Composable
fun Modifier.typeCentered(style: TextStyle): Modifier =
    offset(y = rememberTypeCenteringOffset(style))

/**
 * How far to move a block of lines down so its letters, from the capitals of [firstLine] to the
 * baseline of [lastLine], sit centred as one.
 *
 * The block form of [rememberTypeCenteringOffset], taken against the same reference glyph and so
 * the same for every block sharing those two styles. With one style for both it gives the same
 * number as the single-line form.
 */
@Composable
fun rememberTypeCenteringOffset(firstLine: TextStyle, lastLine: TextStyle): Dp {
    val measurer = rememberTextMeasurer()
    val firstPaint = rememberFontPaint(firstLine)
    val density = LocalDensity.current
    return remember(measurer, firstPaint, firstLine, lastLine, density) {
        val firstInk = Rect().also {
            firstPaint.getTextBounds(CAP_HEIGHT_REFERENCE, 0, CAP_HEIGHT_REFERENCE.length, it)
        }
        val firstLayout = measurer.measure(CAP_HEIGHT_REFERENCE, firstLine)
        val lastLayout = measurer.measure(CAP_HEIGHT_REFERENCE, lastLine)
        val offsetPx = blockCenteringOffsetPx(
            firstBaselinePx = firstLayout.firstBaseline,
            firstInkTopPx = firstInk.top,
            lastBoxHeightPx = lastLayout.size.height.toFloat(),
            lastBaselinePx = lastLayout.lastBaseline,
        )
        with(density) { offsetPx.toDp() }
    }
}

/**
 * Moves one line of a block — a title over its supporting line — so the block's letters sit centred
 * as one against something that is not text, such as the icon or avatar beside it.
 *
 * Give **every line of the block the same call**, with the block's first and last line styles, so
 * they all move by one amount and keep their spacing. Correcting each line by its own style instead
 * adds the lines' corrections together where they should partly cancel — the large first line's
 * room above its capitals is offset by the small last line's room below its baseline — and put the
 * profile card's 22sp name over a 14sp address 4px low, where uncorrected it had been 1.5px high.
 */
@Composable
fun Modifier.typeCentered(firstLine: TextStyle, lastLine: TextStyle): Modifier =
    offset(y = rememberTypeCenteringOffset(firstLine, lastLine))
