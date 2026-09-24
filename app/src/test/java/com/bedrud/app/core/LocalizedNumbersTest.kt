package com.bedrud.app.core

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalizedNumbersTest {

    @Test
    fun `formatCount writes Persian digits for Persian`() {
        assertEquals("۱۲", formatCount(12, persian))
    }

    @Test
    fun `formatCount writes Latin digits for English`() {
        assertEquals("12", formatCount(12, Locale.ENGLISH))
    }

    @Test
    fun `formatCount groups thousands the way the language does`() {
        assertEquals("1,234", formatCount(1_234, Locale.ENGLISH))
    }

    @Test
    fun `formatPercent writes Persian digits for Persian`() {
        val formatted = formatPercent(25, persian)

        assertTrue("expected Persian digits in '$formatted'", formatted.contains("۲۵"))
        assertTrue("expected no Latin digits in '$formatted'", formatted.none { it in '0'..'9' })
    }

    @Test
    fun `formatPercent puts the sign after the number in English`() {
        assertEquals("25%", formatPercent(25, Locale.ENGLISH))
    }

    @Test
    fun `formatPercent puts the sign before the number in Turkish`() {
        // Turkish writes the sign first, which a "$percent%" template can never produce.
        assertEquals("%25", formatPercent(25, Locale.forLanguageTag("tr")))
    }

    @Test
    fun `formatPercent keeps whole percentages whole`() {
        assertEquals("100%", formatPercent(100, Locale.ENGLISH))
    }

    private val persian = Locale.forLanguageTag("fa")
}
