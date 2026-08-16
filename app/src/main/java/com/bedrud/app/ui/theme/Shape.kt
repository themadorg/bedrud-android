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
    val snackbar = RoundedCornerShape(BedrudRadius.md) // snackbars / toasts
    val card = RoundedCornerShape(BedrudRadius.lg)    // cards / selectable tiles
    val chip = RoundedCornerShape(BedrudRadius.sm)
    val pill = RoundedCornerShape(BedrudRadius.full)  // badges, FABs, avatars
    val sheetTop = RoundedCornerShape(topStart = BedrudRadius.xxl, topEnd = BedrudRadius.xxl)
    // The tiles and the bar float on the same background and are read together, so they share a
    // corner. They were 12dp and 28dp, and side by side the two curves plainly disagreed.
    val videoTile = RoundedCornerShape(BedrudRadius.xxl)   // in-meeting video/participant tiles
    val controlsBar = RoundedCornerShape(BedrudRadius.xxl) // floating in-call controls pill
    val chatImage = RoundedCornerShape(BedrudRadius.sm)    // image attached to a chat message

    /**
     * A chat bubble's corners. Everything is [BedrudRadius.lg], except the corners facing the
     * sender's own side, which tighten to [BedrudRadius.xs] where another bubble from the same
     * person sits against them. That tightening is what makes a run of messages read as one block
     * rather than a stack of separate cards.
     */
    fun chatBubble(
        isLocal: Boolean,
        tuckedAbove: Boolean,
        tuckedBelow: Boolean,
    ): RoundedCornerShape {
        val above = if (tuckedAbove) BedrudRadius.xs else BedrudRadius.lg
        val below = if (tuckedBelow) BedrudRadius.xs else BedrudRadius.lg
        return if (isLocal) {
            RoundedCornerShape(
                topStart = BedrudRadius.lg,
                topEnd = above,
                bottomEnd = below,
                bottomStart = BedrudRadius.lg,
            )
        } else {
            RoundedCornerShape(
                topStart = above,
                topEnd = BedrudRadius.lg,
                bottomEnd = BedrudRadius.lg,
                bottomStart = below,
            )
        }
    }
}
