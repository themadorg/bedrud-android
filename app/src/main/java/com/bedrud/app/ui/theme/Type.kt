package com.bedrud.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.bedrud.app.R

/**
 * Vazirmatn, the app's one typeface, in every locale.
 *
 * The font used to be chosen from the *interface* language: Persian got Shabnam, other RTL
 * languages got Vazirmatn, and everyone else got the platform sans. But the script a person types
 * has nothing to do with the language they read the app in — a Persian display name, room name or
 * chat message arrives in an English interface all the time. The platform sans carries no
 * Arabic-script glyphs, so those names fell through to the system fallback, which on a Samsung
 * device is `SECNaskhArabic` in its `elegant` variant: a high-contrast calligraphic book face,
 * sitting inside an 11sp tile chip next to Roboto. Choosing by content instead of by locale is not
 * something a `Typography` can express, so the honest fix is to have one family that covers both
 * scripts and to use it unconditionally.
 *
 * Vazirmatn is that family. Its Latin glyphs *are* Roboto — the project merges them in at build
 * time — so Latin text is unchanged on a device whose sans-serif resolves to Roboto, while Persian
 * and Arabic finally get a UI sans instead of a naskh. It is variable, so the four weights below
 * are real instances of one file rather than four copies.
 *
 * It carries no Cyrillic, Greek or CJK. Russian, Japanese and Chinese therefore still resolve
 * through the platform's fallback chain, exactly as they did before this became the base font.
 */
private fun buildVazirmatnFamily(): FontFamily {
    @OptIn(ExperimentalTextApi::class)
    return FontFamily(
        Font(R.font.vazirmatn, weight = FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.Setting("wght", 400f))),
        Font(R.font.vazirmatn, weight = FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.Setting("wght", 500f))),
        Font(R.font.vazirmatn, weight = FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.Setting("wght", 600f))),
        Font(R.font.vazirmatn, weight = FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.Setting("wght", 700f)))
    )
}

val VazirmatnFontFamily = buildVazirmatnFamily()

/**
 * The Material 3 type scale, bound to [fontFamily].
 *
 * Sizes, weights and letter spacing are the M3 defaults; only the family is ours. Kept as a
 * function taking the family so the scale is written once, rather than repeating the same
 * assignment on all fifteen styles.
 */
private fun typographyWith(fontFamily: FontFamily) = Typography(
    displayLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp
    ),
    displaySmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

val BedrudTypography = typographyWith(VazirmatnFontFamily)
