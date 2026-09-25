package com.bedrud.app.ui.theme

import java.io.File
import java.nio.ByteBuffer
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks the companion face bundled behind Vazirmatn, read straight from the file the app ships.
 *
 * The file is cut down from Roboto by `tools/fonts/build-companion-font.sh`, so these are the
 * promises a rerun of that script has to keep: every letter it is there for, and the weight axis
 * that lets the type scale's four weights be real weights rather than one regular face.
 */
class TypeTest {

    private val companionFile = File("src/main/res/font/roboto_cyrillic_greek.ttf")

    private fun companionFont(): java.awt.Font =
        java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, companionFile)

    private fun missingFrom(letters: String): List<String> {
        val font = companionFont()
        return letters.codePoints().toArray()
            .filterNot { font.canDisplay(it) }
            .map { String(Character.toChars(it)) }
    }

    /** Finds a top-level table in the font's table directory, by its four-letter tag. */
    private fun table(font: ByteBuffer, tag: String): ByteBuffer? {
        val tableCount = font.getShort(TableCountOffset).toInt()
        for (index in 0 until tableCount) {
            val record = TableDirectoryOffset + index * TableRecordSize
            val recordTag = String(ByteArray(TagSize) { font.get(record + it) }, Charsets.US_ASCII)
            if (recordTag == tag) {
                val start = font.getInt(record + TableOffsetField)
                val length = font.getInt(record + TableLengthField)
                val window = font.duplicate()
                window.position(start)
                window.limit(start + length)
                return window.slice()
            }
        }
        return null
    }

    /** Reads the range of one variation axis from the `fvar` table, or null if it has none. */
    private fun axisRange(tag: String): ClosedFloatingPointRange<Float>? {
        val font = ByteBuffer.wrap(companionFile.readBytes())
        val variations = table(font, "fvar") ?: return null
        val axesStart = variations.getShort(FvarAxesOffsetField).toInt()
        val axisCount = variations.getShort(FvarAxisCountField).toInt()
        val axisSize = variations.getShort(FvarAxisSizeField).toInt()
        for (index in 0 until axisCount) {
            val axis = axesStart + index * axisSize
            val axisTag = String(ByteArray(TagSize) { variations.get(axis + it) }, Charsets.US_ASCII)
            if (axisTag == tag) {
                val minimum = variations.getInt(axis + AxisMinimumField) / FixedPointOne
                val maximum = variations.getInt(axis + AxisMaximumField) / FixedPointOne
                return minimum..maximum
            }
        }
        return null
    }

    @Test
    fun `should carry every letter of the Russian alphabet`() {
        assertTrue(missingFrom(RussianLetters).joinToString(), missingFrom(RussianLetters).isEmpty())
    }

    @Test
    fun `should carry the Cyrillic letters other languages add to Russian's`() {
        assertTrue(missingFrom(OtherCyrillicLetters).joinToString(), missingFrom(OtherCyrillicLetters).isEmpty())
    }

    @Test
    fun `should carry the modern Greek alphabet with its accents`() {
        assertTrue(missingFrom(GreekLetters).joinToString(), missingFrom(GreekLetters).isEmpty())
    }

    @Test
    fun `should carry the stress mark on its Cyrillic letter`() {
        assertTrue(missingFrom(CombiningAcute).isEmpty())
    }

    @Test
    fun `should keep a weight axis spanning every weight the type scale uses`() {
        val weights = axisRange("wght")
        assertTrue("no wght axis", weights != null)
        assertTrue("wght axis $weights", TypeScaleWeights.all { it in weights!! })
    }

    private companion object {
        const val RussianLetters =
            "АБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯабвгдеёжзийклмнопрстуфхцчшщъыьэюя"

        /** Ukrainian, Belarusian, Serbian, Macedonian and Kazakh letters, which names arrive in. */
        const val OtherCyrillicLetters = "ҐґЄєІіЇїЎўЂђЈјЉљЊњЋћЏџЃѓЌќЅѕҚқҢңҮүҰұӘәӨө"

        const val GreekLetters =
            "ΑΒΓΔΕΖΗΘΙΚΛΜΝΞΟΠΡΣΤΥΦΧΨΩαβγδεζηθικλμνξοπρσςτυφχψωάέήίόύώΆΈΉΊΌΎΏϊϋΐΰ"

        /**
         * The combining acute that marks stress in Russian. Vazirmatn carries it too, but Android
         * keeps a mark in its letter's font only when that font has it, so the companion needs its
         * own copy for the mark to sit on a Cyrillic vowel drawn by Roboto.
         */
        const val CombiningAcute = "́"

        /** The weights `Type.kt` registers: Normal, Medium, SemiBold, Bold. */
        val TypeScaleWeights = listOf(400f, 500f, 600f, 700f)

        // OpenType table directory and `fvar` layout, from the OpenType specification.
        const val TableCountOffset = 4
        const val TableDirectoryOffset = 12
        const val TableRecordSize = 16
        const val TableOffsetField = 8
        const val TableLengthField = 12
        const val TagSize = 4
        const val FvarAxesOffsetField = 4
        const val FvarAxisCountField = 8
        const val FvarAxisSizeField = 10
        const val AxisMinimumField = 4
        const val AxisMaximumField = 12
        const val FixedPointOne = 65536f
    }
}
