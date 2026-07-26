package com.bedrud.app.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing

/**
 * Motion tokens — durations + easing for UI transitions (selection, expand/collapse, emphasis),
 * so animations stay consistent instead of each call site inventing its own timing.
 *
 * Use with `tween(Motion.durationMedium, easing = Motion.standardEasing)` in `animate*AsState`.
 */
object Motion {
    const val durationShort = 150
    const val durationMedium = 250
    const val durationLong = 400

    val standardEasing = FastOutSlowInEasing
}
