package com.bedrud.app.ui.theme

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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Shapes text in the app's own type scale and asks Android which font drew each glyph.
 *
 * A screenshot only shows that *something* drew the letters. The shaper names the font file behind
 * every glyph, and the weight it was drawn at, so this is what proves Cyrillic comes from the
 * bundled companion rather than from whatever the device's own fallback happens to be.
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

    private fun Font.weightAxis(): Float? = axes?.firstOrNull { it.tag == "wght" }?.styleValue

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
    fun shouldDrawCyrillicAtEveryWeightTheTypeScaleAsksFor() {
        for (weight in TypeScaleWeights) {
            val drawn = fontsDrawing(Russian, weight).map { it.weightAxis() }.distinct()
            assertEquals("at $weight", listOf(weight.weight.toFloat()), drawn)
        }
    }

    @Test
    fun shouldDrawLatinAtEveryWeightTheTypeScaleAsksFor() {
        for (weight in TypeScaleWeights) {
            val drawn = fontsDrawing(Latin, weight).map { it.weightAxis() }.distinct()
            assertEquals("at $weight", listOf(weight.weight.toFloat()), drawn)
        }
    }

    private companion object {
        const val ShapingTextSize = 40f
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
