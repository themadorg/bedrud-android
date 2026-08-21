package com.bedrud.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.bedrud.app.ui.theme.Alpha
import com.bedrud.app.ui.theme.BedrudShapeTokens
import com.bedrud.app.ui.theme.Dimens

/**
 * The app's bottom sheet. Every sheet in the app is one of these.
 *
 * Container, drag handle, shape, insets and gutter are **fixed, not defaulted**. There is
 * deliberately no colour parameter: a default is a suggestion, and the one sheet that took the
 * suggestion up ended up rendering its container at `surface` — the exact colour of the screen
 * behind it — so it had no edge at all with the camera off. M3's `surfaceContainerLow` exists to
 * lift a sheet off the background, and it is opaque over video just the same, so there is no case
 * where a darker container buys anything.
 *
 * The handle is [BedrudSheetHandle] rather than M3's own. The Material one pressed as a rounded rectangle splashing across its whole touch
 * area, and announced itself as "Drag Handle" — a label Android shows on long press, naming the
 * widget instead of saying what it does. The shape token stays: [BedrudShapeTokens.sheetTop] is
 * already M3's 28dp `extraLarge` top corners, just named.
 *
 * The sheet state is deliberately **not** a parameter either. Exposing it would put an
 * experimental Material type in the signature, forcing `@OptIn` onto every screen that shows a
 * sheet — re-leaking the Material detail this component exists to contain. Sheets are dismissed
 * the way the rest of the app dismisses them: the caller stops composing them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BedrudBottomSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = sheetState,
        shape = BedrudShapeTokens.sheetTop,
        containerColor = BottomSheetDefaults.ContainerColor,
        dragHandle = { BedrudSheetHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // imePadding is unconditional: it resolves to zero with the keyboard down, so the
                // sheets that carry an input need no special case. navigationBars first, so the
                // consumed inset is not counted twice once the keyboard is up.
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = Dimens.sheetPadding)
                .padding(bottom = Dimens.space24),
            verticalArrangement = Arrangement.spacedBy(Dimens.space4),
            content = content,
        )
    }
}

/**
 * A title above a sheet's content. Kept here so every sheet's header sits at the same size and
 * inset instead of each one picking its own.
 */
@Composable
fun BedrudSheetTitle(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier.padding(horizontal = Dimens.space4, vertical = Dimens.space8),
    )
}

/**
 * One action in a sheet, as an M3 list item: leading icon, label, and an optional line saying what
 * the action does.
 *
 * Deliberately **not** wrapped in a card or filled surface — M3 reserves per-item containers for
 * selectable cards, and a list of actions is a list, not a stack of cards. [contentColor] carries
 * the emphasis instead (e.g. the error colour for a destructive choice), which also avoids relying
 * on a border: `outlineVariant` sits within a couple of RGB units of a raised surface in the dark
 * palette, so an outline here would be invisible.
 *
 * A row that does not apply right now takes [enabled] `false` rather than being left out — the
 * sheet keeps its height, so toggling one setting never makes the rows under it jump.
 */
@Composable
fun BedrudSheetActionRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    supportingColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    enabled: Boolean = true,
    trailing: @Composable (() -> Unit)? = null,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        color = Color.Transparent,
        shape = BedrudShapeTokens.card,
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else Alpha.disabled),
    ) {
        Row(
            modifier = Modifier
                .defaultMinSize(
                    minHeight = if (supportingText == null) Dimens.sheetRowHeight
                    else Dimens.sheetRowHeightTwoLine
                )
                .padding(horizontal = Dimens.space12),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.space16),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(Dimens.iconMd),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = contentColor,
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (supportingText != null) {
                    Text(
                        text = supportingText,
                        color = supportingColor,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            trailing?.invoke()
        }
    }
}
