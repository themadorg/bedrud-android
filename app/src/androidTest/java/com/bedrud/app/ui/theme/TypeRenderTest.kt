package com.bedrud.app.ui.theme

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.fonts.Font
import android.graphics.text.TextRunShaper
import android.os.Build
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.font.resolveAsTypeface
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.bedrud.app.R
import java.nio.ByteBuffer
import kotlin.math.ceil
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Shapes text in the app's own type scale and asks Android which font drew each glyph.
 *
 * A screenshot only shows that *something* drew the letters. The shaper names the font file behind
 * every glyph, so this is what proves Cyrillic comes from the bundled companion rather than from
 * whatever the device's own fallback happens to be. Weight is measured on the drawn ink instead.
 *
 * Needs API 31 for `TextRunShaper`, and is skipped below it; the fallback itself works from API 29.
 */
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
class TypeRenderTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val resolver = createFontFamilyResolver(context)
    private val fontFamily = BedrudTypography.bodyLarge.fontFamily!!

    private fun fileOf(resource: Int): ByteBuffer =
        context.resources.openRawResource(resource).use { ByteBuffer.wrap(it.readBytes()) }

    private val vazirmatn = fileOf(R.font.vazirmatn)
    private val companion = fileOf(R.font.roboto_cyrillic_greek)

    /** The font behind every glyph Android draws for [text] at [weight]. */
    private fun fontsDrawing(text: String, weight: FontWeight = FontWeight.Normal): List<Font> {
        val paint = Paint().apply {
            typeface = resolver.resolveAsTypeface(fontFamily, weight).value
            textSize = ShapingTextSize
        }
        val glyphs = TextRunShaper.shapeTextRun(
            text, 0, text.length, 0, text.length, 0f, 0f, false, paint,
        )
        return (0 until glyphs.glyphCount()).map { glyphs.getFont(it) }
    }

    private fun Font.isFrom(file: ByteBuffer): Boolean {
        val drawn = buffer.duplicate()
        drawn.rewind()
        val expected = file.duplicate()
        expected.rewind()
        return drawn == expected
    }

    /**
     * How much ink [text] lays down at [weight]: the summed coverage of every pixel it draws.
     *
     * Measured on drawn pixels because newer Android no longer reports a shaped font's variation
     * axes, so asking the shaper which `wght` it used answers nothing from API 36.
     */
    private fun inkAt(text: String, weight: FontWeight): Long {
        val paint = Paint().apply {
            typeface = resolver.resolveAsTypeface(fontFamily, weight).value
            textSize = InkTextSize
            isAntiAlias = true
        }
        val metrics = paint.fontMetricsInt
        val bitmap = Bitmap.createBitmap(
            ceil(paint.measureText(text)).toInt() + InkMarginPx * 2,
            metrics.bottom - metrics.top + InkMarginPx * 2,
            Bitmap.Config.ALPHA_8,
        )
        Canvas(bitmap).drawText(text, InkMarginPx.toFloat(), (InkMarginPx - metrics.top).toFloat(), paint)
        val pixels = ByteBuffer.allocate(bitmap.byteCount)
        bitmap.copyPixelsToBuffer(pixels)
        return pixels.array().sumOf { (it.toInt() and 0xFF).toLong() }
    }

    /** Fails unless every weight of the scale draws [text] with more ink than the one before it. */
    private fun assertHeavierAtEveryStep(text: String) {
        val ink = TypeScaleWeights.map { inkAt(text, it) }
        assertTrue(
            "ink at ${TypeScaleWeights.map { it.weight }}: $ink",
            ink.zipWithNext().all { (lighter, heavier) -> lighter < heavier },
        )
    }

    @Test
    fun shouldDrawCyrillicWithTheBundledCompanion() {
        assertTrue(fontsDrawing(Russian).all { it.isFrom(companion) })
    }

    @Test
    fun shouldDrawGreekWithTheBundledCompanion() {
        assertTrue(fontsDrawing(Greek).all { it.isFrom(companion) })
    }

    @Test
    fun shouldKeepStressMarkInTheSameFontAsItsCyrillicVowel() {
        assertTrue(fontsDrawing(StressedVowel).all { it.isFrom(companion) })
    }

    @Test
    fun shouldDrawLatinWithVazirmatn() {
        assertTrue(fontsDrawing(Latin).all { it.isFrom(vazirmatn) })
    }

    @Test
    fun shouldDrawPersianWithVazirmatn() {
        assertTrue(fontsDrawing(Persian).all { it.isFrom(vazirmatn) })
    }

    @Test
    fun shouldLeaveChineseToThePlatform() {
        assertTrue(fontsDrawing(Chinese).none { it.isFrom(vazirmatn) || it.isFrom(companion) })
    }

    @Test
    fun shouldDrawCyrillicHeavierAtEveryWeightTheTypeScaleAsksFor() {
        assertHeavierAtEveryStep(Russian)
    }

    @Test
    fun shouldDrawLatinHeavierAtEveryWeightTheTypeScaleAsksFor() {
        assertHeavierAtEveryStep(Latin)
    }

    private companion object {
        const val ShapingTextSize = 40f

        /** Large enough that one step of the weight axis changes the ink by whole pixels. */
        const val InkTextSize = 120f

        /** Room around the text so no anti-aliased edge is cut off by the bitmap's border. */
        const val InkMarginPx = 8
        const val Russian = "Привет"
        const val Greek = "Καλημέρα"
        const val StressedVowel = "а́"
        const val Latin = "Bedrud"
        const val Persian = "سلام"
        const val Chinese = "你好"
        val TypeScaleWeights =
            listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)
    }
}
