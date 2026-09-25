package com.bedrud.app.ui.theme

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.AndroidFont
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.bedrud.app.R
import android.graphics.fonts.Font as PlatformFont
import android.graphics.fonts.FontFamily as PlatformFontFamily
import android.graphics.fonts.FontStyle as PlatformFontStyle

/** The weights the type scale uses. Each is a real instance of the fonts' `wght` axis. */
private val TypeScaleWeights =
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)

/**
 * Vazirmatn alone, as the app drew every script before a companion face was bundled.
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
 * Used as it is below API 29, where Android offers no way to add a font of the app's own to the
 * fallback chain; Cyrillic, Greek and CJK resolve through the platform's fallback there.
 */
private fun buildVazirmatnFamily(): FontFamily {
    @OptIn(ExperimentalTextApi::class)
    return FontFamily(
        TypeScaleWeights.map { weight ->
            Font(
                R.font.vazirmatn,
                weight = weight,
                variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
            )
        }
    )
}

/** Reads one of the app's bundled fonts as an instance of its `wght` axis at [weight]. */
@RequiresApi(Build.VERSION_CODES.Q)
private fun platformFontAt(context: Context, resource: Int, weight: Int): PlatformFont =
    PlatformFont.Builder(context.resources, resource)
        .setWeight(weight)
        .setFontVariationSettings("'wght' $weight")
        .build()

/**
 * Builds the typeface for one weight of the scale: Vazirmatn, then the bundled companion, then the
 * platform's own sans-serif fallback.
 *
 * Android walks that chain per character, so a Russian name inside Persian text takes its letters
 * from the companion and everything else from Vazirmatn. Both fonts are read at the same `wght`,
 * which keeps a Bold title bold in either script.
 */
@RequiresApi(Build.VERSION_CODES.Q)
private object ScriptFallbackLoader : AndroidFont.TypefaceLoader {
    /** The platform family whose fallback chain covers every script neither bundled font does. */
    private const val SystemFallbackFamily = "sans-serif"

    override fun loadBlocking(context: Context, font: AndroidFont): Typeface {
        val weight = font.weight.weight
        val vazirmatn = platformFontAt(context, R.font.vazirmatn, weight)
        val companion = platformFontAt(context, R.font.roboto_cyrillic_greek, weight)
        return Typeface.CustomFallbackBuilder(PlatformFontFamily.Builder(vazirmatn).build())
            .addCustomFallback(PlatformFontFamily.Builder(companion).build())
            .setSystemFallback(SystemFallbackFamily)
            .setStyle(PlatformFontStyle(weight, PlatformFontStyle.FONT_SLANT_UPRIGHT))
            .build()
    }

    override suspend fun awaitLoad(context: Context, font: AndroidFont): Typeface =
        loadBlocking(context, font)
}

/**
 * One weight of the scale, loaded as a whole fallback chain rather than a single file.
 *
 * Compose picks one font per weight and never looks past it for a missing letter, so the chain has
 * to be built by Android and handed over as a single typeface. It is handed over through a font of
 * its own rather than `FontFamily(Typeface)`, because Compose returns a wrapped typeface unchanged
 * for every weight it is asked for, which would flatten the whole scale to a single weight.
 */
@RequiresApi(Build.VERSION_CODES.Q)
private class ScriptFallbackFont(override val weight: FontWeight) : AndroidFont(
    loadingStrategy = FontLoadingStrategy.Blocking,
    typefaceLoader = ScriptFallbackLoader,
    variationSettings = FontVariation.Settings(),
) {
    override val style: FontStyle = FontStyle.Normal
}

/**
 * The app's one typeface, in every locale: Vazirmatn for Latin and the Arabic script, backed by a
 * cut of Roboto for Cyrillic and Greek.
 *
 * Vazirmatn carries no Cyrillic or Greek, and before the companion was bundled those letters came
 * from whatever the device's own fallback was — stock Roboto on one phone, an owner's themed font
 * on the next. Roboto is the companion because Vazirmatn's Latin already *is* Roboto, so a Latin
 * word inside Russian text stays in the same design. CJK is left to the platform: a face covering
 * it would cost around 16 MB.
 */
val BedrudFontFamily: FontFamily =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        FontFamily(TypeScaleWeights.map { ScriptFallbackFont(it) })
    } else {
        buildVazirmatnFamily()
    }

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

val BedrudTypography = typographyWith(BedrudFontFamily)
