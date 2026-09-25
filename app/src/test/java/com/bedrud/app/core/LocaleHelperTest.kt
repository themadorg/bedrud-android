package com.bedrud.app.core

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class LocaleHelperTest {

    @Test
    fun `resolveAppLocale uses the language picked in the app`() {
        assertEquals(Locale.forLanguageTag("fa"), resolveAppLocale(localeTag = "fa", deviceLocale = Locale.US))
    }

    @Test
    fun `resolveAppLocale uses the device language when the choice is System`() {
        assertEquals(Locale.US, resolveAppLocale(localeTag = "", deviceLocale = Locale.US))
    }

    @Test
    fun `resolveAppLocale ignores the default an earlier pick left behind`() {
        // Applying a pick overwrites Locale.getDefault(), so a System choice that read the default
        // kept answering with the language just left behind until the process was killed.
        val originalDefault = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("fa"))

            assertEquals(Locale.US, resolveAppLocale(localeTag = "", deviceLocale = Locale.US))
        } finally {
            Locale.setDefault(originalDefault)
        }
    }
}
