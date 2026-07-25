package com.bedrud.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic colors Material 3's [androidx.compose.material3.ColorScheme] has no slot for.
 *
 * Currently a `warning` role (amber) for non-critical cautions — kept distinct from `error` (red),
 * which is reserved for errors and destructive/irreversible actions.
 *
 * Provided via [LocalBedrudColors] in [BedrudTheme]; read through `MaterialTheme.bedrudColors`.
 */
data class BedrudExtendedColors(
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
)

val LightExtendedColors = BedrudExtendedColors(
    warning = Amber700,
    onWarning = Neutral0,
    warningContainer = Amber100,
    onWarningContainer = Amber900,
)

val DarkExtendedColors = BedrudExtendedColors(
    warning = Amber400,
    onWarning = Amber950,
    warningContainer = Amber900,
    onWarningContainer = Amber100,
)

val LocalBedrudColors = staticCompositionLocalOf { LightExtendedColors }

/** Bedrud's extended (non-M3) semantic colors, e.g. `MaterialTheme.bedrudColors.warning`. */
val MaterialTheme.bedrudColors: BedrudExtendedColors
    @Composable
    @ReadOnlyComposable
    get() = LocalBedrudColors.current
