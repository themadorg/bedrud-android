package com.bedrud.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Corner + shape tokens. The app is rounded/Material-3-native (no sharp corners).
 * Screens/components reference [BedrudShapeTokens] (or `MaterialTheme.shapes`) — never a raw
 * `RoundedCornerShape(n.dp)` literal.
 */
object BedrudRadius {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 28.dp
    val full = 999.dp
}

/** The Material 3 shape scale, wired into [BedrudTheme] so all M3 components inherit it. */
val BedrudShapes = Shapes(
    extraSmall = RoundedCornerShape(BedrudRadius.xs),
    small = RoundedCornerShape(BedrudRadius.sm),
    medium = RoundedCornerShape(BedrudRadius.md),
    large = RoundedCornerShape(BedrudRadius.lg),
    extraLarge = RoundedCornerShape(BedrudRadius.xxl),
)

/** Semantic shapes for Bedrud components, so intent is explicit at call sites. */
object BedrudShapeTokens {
    val field = RoundedCornerShape(BedrudRadius.md)   // text fields
    val button = RoundedCornerShape(BedrudRadius.md)  // buttons
    val card = RoundedCornerShape(BedrudRadius.lg)    // cards / selectable tiles
    val chip = RoundedCornerShape(BedrudRadius.sm)
    val pill = RoundedCornerShape(BedrudRadius.full)  // badges, FABs, avatars
    val sheetTop = RoundedCornerShape(topStart = BedrudRadius.xxl, topEnd = BedrudRadius.xxl)
}
