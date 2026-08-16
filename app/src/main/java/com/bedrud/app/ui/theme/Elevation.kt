package com.bedrud.app.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Elevation tokens (Material 3 tonal elevation levels). The app leans on outlines + tonal
 * surfaces rather than heavy shadows, so most surfaces stay at [level0]/[level1].
 */
object Elevation {
    val level0 = 0.dp
    val level1 = 1.dp
    val level2 = 3.dp
    val level3 = 6.dp
    val level4 = 8.dp
    val level5 = 12.dp

    // In-call floating controls pill — semantic pair, kept at the bar's established depth.
    val controlsBarShadow = 4.dp
    val controlsBarTonal = 2.dp

    // The mic key stands proud of the bar so it reads as pressable, and sinks flush under a
    // press — the depth change is what sells "hold me" on a control that is held.
    val micPillResting = 3.dp
    val micPillPressed = 0.dp
}
