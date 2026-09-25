package com.bedrud.app.core

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import java.util.Locale

/**
 * The device's own language, as set in the system settings.
 *
 * Read from the system resources rather than from `Locale.getDefault()`: [createLocaleContext]
 * overwrites that default with whatever language it applies, so once the app has run in a language
 * picked in Settings, the default no longer says anything about the device.
 */
fun deviceLocale(): Locale = Resources.getSystem().configuration.locales[0]

/**
 * The locale the app's text is drawn in: the language picked in Settings, or [deviceLocale] when
 * the choice is System — which is also what a first run gets, since nothing has been picked yet.
 */
fun resolveAppLocale(localeTag: String, deviceLocale: Locale): Locale =
    if (localeTag.isNotEmpty()) Locale.forLanguageTag(localeTag) else deviceLocale

fun Context.createLocaleContext(localeTag: String): Context {
    val locale = resolveAppLocale(localeTag, deviceLocale())
    Locale.setDefault(locale)
    val config = Configuration(this.resources.configuration).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setLocales(android.os.LocaleList(locale))
        } else {
            @Suppress("DEPRECATION")
            setLocale(locale)
        }
    }
    return createConfigurationContext(config)
}
