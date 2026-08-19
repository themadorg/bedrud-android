package com.bedrud.app.ui.theme

import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
 * The avatar, the chat bubble and the reaction chip go through here. Everywhere else in the app
 * still centres the box; TODO(#137) tracks the sweep.
 */
@Composable
fun rememberInkCenteringOffset(text: String, style: TextStyle): Dp {
    val paint = rememberFontPaint(style)
    val density = LocalDensity.current
    return remember(paint, text, density) {
        if (text.isEmpty()) return@remember 0.dp
        val ink = Rect().also { paint.getTextBounds(text, 0, text.length, it) }
        if (ink.isEmpty) return@remember 0.dp
        val metrics = paint.fontMetrics
        // Both are measured from the baseline and both run negative upwards, so the difference is
        // simply how far the letters sit above where the box is centred.
        val boxCenter = (metrics.ascent + metrics.descent) / 2f
        val inkCenter = (ink.top + ink.bottom) / 2f
        with(density) { (boxCenter - inkCenter).toDp() }
    }
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
