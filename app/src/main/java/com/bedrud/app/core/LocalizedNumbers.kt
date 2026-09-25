package com.bedrud.app.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import java.text.NumberFormat
import java.util.Locale

private const val PERCENT_PER_WHOLE = 100.0

/**
 * A number standing on its own — a badge, a tally, a stat — written the way [locale] writes
 * numbers: its own digits (Persian writes `۱۲`), thousands grouped the way the language groups them.
 *
 * A number inside a sentence goes through its string resource's `%1$d` instead, which the
 * resources already format in the app's language; this is for the ones with no sentence around
 * them, which a `toString()` or string template would otherwise leave in Latin digits.
 */
fun formatCount(value: Long, locale: Locale): String = NumberFormat.getIntegerInstance(locale).format(value)

/** [formatCount] for an [Int]. */
fun formatCount(value: Int, locale: Locale): String = formatCount(value.toLong(), locale)

/**
 * A whole percentage, 0 to 100, with the language's own digits, sign and sign position — `25%`,
 * `%25` in Turkish, `25 %` in French, `۲۵٪` in Persian. A `"$value%"` template gets all three wrong
 * somewhere.
 */
fun formatPercent(percent: Int, locale: Locale): String =
    NumberFormat.getPercentInstance(locale).format(percent / PERCENT_PER_WHOLE)

/** The language the app is drawn in, for the formatters above. */
@Composable
@ReadOnlyComposable
fun appLocale(): Locale = LocalConfiguration.current.locales[0]
