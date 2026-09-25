package com.bedrud.app.testutil

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.text.TextLayoutResult
import com.bedrud.app.ui.theme.BedrudTheme

/** The font scales a reader can choose in the system's font-size setting. */
object FontScales {
    const val Default = 1f

    /** One of the intermediate steps of the system's font-size setting. */
    const val Raised = 1.3f

    const val Large = 1.5f

    /** The largest scale Android offers, since Android 14. */
    const val Largest = 2f
}

/** Shows [content] in the app theme, laid out as it would be at the reader's [fontScale]. */
fun ComposeContentTestRule.setThemedContentAt(fontScale: Float, content: @Composable () -> Unit) {
    setContent {
        DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(fontScale)) {
            BedrudTheme { content() }
        }
    }
}

/**
 * The layout the node's text was given.
 *
 * Width tells nothing about a squeezed label — a layout's paragraph is as wide as the space it was
 * offered, not as wide as the text it drew — so a squeezed label is told by its line count.
 */
fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
    val layouts = mutableListOf<TextLayoutResult>()
    val action = fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action
    action?.invoke(layouts)
    return layouts.single()
}

/**
 * Whether the text's box is shorter than the lines laid out in it, which cuts them off when drawn.
 *
 * Unlike [TextLayoutResult.didOverflowHeight], text shortened on purpose — a line limit with an
 * ellipsis — does not count: its box still holds every line it draws.
 */
val TextLayoutResult.clipsLines: Boolean
    get() = size.height < multiParagraph.height
