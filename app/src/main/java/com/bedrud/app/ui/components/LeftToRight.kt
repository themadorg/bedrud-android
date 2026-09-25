package com.bedrud.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

/**
 * Lays [content] out left-to-right in every language.
 *
 * For what is a fixed arrangement or a picture rather than a reading direction: the call's control
 * bars keep one order in every language, and a speaker keeps facing the way it is drawn. Material
 * mirrors an auto-mirrored icon by the layout direction it is drawn in, so this is also how one of
 * those icons is kept as drawn. Text inside still reads in its own direction; content that should
 * follow the app's language again provides the direction it captured from outside.
 */
@Composable
fun LeftToRight(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr, content = content)
}
