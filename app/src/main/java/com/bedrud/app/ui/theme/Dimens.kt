package com.bedrud.app.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Spacing + sizing tokens. Every gap, padding, component height, and icon size in the UI comes
 * from here instead of a raw `n.dp` literal, so the layout rhythm stays consistent and tunable.
 *
 * Spacing follows a 4dp base grid.
 */
object Dimens {
    // ── Spacing scale (4dp grid) ──
    val space2 = 2.dp
    val space4 = 4.dp
    val space6 = 6.dp
    val space8 = 8.dp
    val space12 = 12.dp
    val space16 = 16.dp
    val space20 = 20.dp
    val space24 = 24.dp
    val space32 = 32.dp
    val space40 = 40.dp
    val space48 = 48.dp
    val space56 = 56.dp

    // ── Layout ──
    val screenPadding = 24.dp
    val screenPaddingCompact = 16.dp
    val maxContentWidth = 480.dp   // keep forms readable on tablets/foldables

    // ── Components ──
    val buttonHeight = 48.dp
    val buttonHeightLarge = 56.dp
    val minTouchTarget = 48.dp
    val fieldMinHeight = 56.dp
    val cardPadding = 16.dp
    val borderThin = 1.dp
    val borderStrong = 2.dp

    // ── Icons / avatars ──
    val iconXs = 16.dp
    val iconSm = 18.dp
    val iconMd = 24.dp
    val iconLg = 32.dp
    val avatar = 40.dp

    // ── Brand ──
    val brandMark = 72.dp
}
