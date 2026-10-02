package com.bedrud.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.bedrud.app.ui.theme.BedrudShapeTokens
import com.bedrud.app.ui.theme.Dimens

/**
 * The grab bar every sheet in the app wears. One handle, one shape, one touch target, one press
 * feedback. The call's controls bar draws the same bar in a shorter 16dp strip of its own, because
 * a 48dp handle there covered the bottom of the video.
 *
 * Replaces M3's `BottomSheetDefaults.DragHandle`, which was kept for a while on the grounds that it
 * carried the accessibility semantics a bare `Box` does not. It carried two other things with it:
 * a press ripple that splashed a rounded rectangle across its whole touch area — reading as a button
 * being pressed rather than a handle being grabbed — and a "Drag Handle" label that Android surfaces
 * as a long-press tooltip, naming the widget instead of saying what it does.
 *
 * The bar is M3's 32×4dp, centred in a full 48dp-tall touch target, as M3's own handle is: a 4dp bar
 * is hard to find with a finger, and a shorter area left the handle a strip most taps missed.
 *
 * Clipping to the pill *before* the click makes the ripple follow the shape. [onClick] is optional:
 * a sheet with one height has nothing for a tap to do, so it gets a plain bar with no ripple and no
 * semantics at all — the sheet is still dragged and dismissed the usual ways.
 */
@Composable
fun BedrudSheetHandle(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
) {
    Box(
        modifier = modifier
            .clip(BedrudShapeTokens.pill)
            .let { base ->
                if (onClick == null) {
                    base
                } else {
                    base
                        .clickable(onClick = onClick)
                        .semantics { if (onClickLabel != null) contentDescription = onClickLabel }
                }
            }
            .padding(horizontal = Dimens.space16)
            .heightIn(min = Dimens.minTouchTarget),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(Dimens.meetingHandleWidth)
                .height(Dimens.meetingHandleHeight)
                .background(
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = HandleAlpha),
                    BedrudShapeTokens.pill,
                ),
        )
    }
}

/** Present without competing with the sheet's own content — the weight the call's handle uses. */
private const val HandleAlpha = 0.55f
