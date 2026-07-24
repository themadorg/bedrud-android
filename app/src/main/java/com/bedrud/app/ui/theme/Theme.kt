package com.bedrud.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.bedrud.app.ui.screens.settings.AppLanguage

/**
 * System color roles for Bedrud, mapped from the reference ramps in [Color.kt].
 *
 * Rose is the brand primary; teal is the tertiary accent; neutrals are warm (stone).
 * The full Material 3 role set is specified (containers, surface-tonal levels, inverse, scrim)
 * so components that reach for any role get an on-brand value instead of an M3 default.
 */
private val LightColorScheme = lightColorScheme(
    primary = Rose600,
    onPrimary = Neutral0,
    primaryContainer = Rose100,
    onPrimaryContainer = Rose900,
    inversePrimary = Rose300,

    secondary = Mauve600,
    onSecondary = Neutral0,
    secondaryContainer = Mauve100,
    onSecondaryContainer = Mauve900,

    tertiary = Teal600,
    onTertiary = Neutral0,
    tertiaryContainer = Teal100,
    onTertiaryContainer = Teal900,

    error = Red600,
    onError = Neutral0,
    errorContainer = Red100,
    onErrorContainer = Red900,

    background = WarmWhite,
    onBackground = Stone900,
    surface = WarmWhite,
    onSurface = Stone900,
    surfaceVariant = Stone200,
    onSurfaceVariant = Stone600,
    surfaceTint = Rose600,
    inverseSurface = Stone900,
    inverseOnSurface = Neutral100,

    outline = Stone400,
    outlineVariant = Stone200,
    scrim = Color.Black,

    surfaceBright = WarmWhite,
    surfaceDim = Neutral250,
    surfaceContainerLowest = Neutral0,
    surfaceContainerLow = Neutral50,
    surfaceContainer = Neutral100,
    surfaceContainerHigh = Neutral150,
    surfaceContainerHighest = Neutral200,
)

private val DarkColorScheme = darkColorScheme(
    primary = Rose400,
    onPrimary = Rose950,
    primaryContainer = Rose800,
    onPrimaryContainer = Rose100,
    inversePrimary = Rose600,

    secondary = MauveDark300,
    onSecondary = MauveDark900,
    secondaryContainer = MauveDark700,
    onSecondaryContainer = Mauve100,

    tertiary = Teal300,
    onTertiary = Teal950,
    tertiaryContainer = Teal800,
    onTertiaryContainer = Teal100,

    error = Red400,
    onError = Red950,
    errorContainer = Red900,
    onErrorContainer = Red100,

    background = Stone950,
    onBackground = NeutralDarkText,
    surface = Stone950,
    onSurface = NeutralDarkText,
    surfaceVariant = Stone800,
    onSurfaceVariant = Stone400,
    surfaceTint = Rose400,
    inverseSurface = NeutralDarkText,
    inverseOnSurface = Stone900,

    outline = Stone600,
    outlineVariant = Stone800,
    scrim = Color.Black,

    surfaceBright = NeutralDark200,
    surfaceDim = Stone950,
    surfaceContainerLowest = NeutralDark0,
    surfaceContainerLow = NeutralDark50,
    surfaceContainer = NeutralDark100,
    surfaceContainerHigh = NeutralDark150,
    surfaceContainerHighest = NeutralDark200,
)

@Composable
fun BedrudTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    language: AppLanguage = AppLanguage.SYSTEM,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val isRtl = language.resolveIsRtl()
    val layoutDirection = if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    val typography = when {
        language.usesShabnam() -> ShabnamTypography
        isRtl -> VazirmatnTypography
        else -> BedrudTypography
    }

    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = BedrudShapes,
            content = content
        )
    }
}
