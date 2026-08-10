package com.bedrud.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.bedrud.app.ui.theme.BedrudShapeTokens
import com.bedrud.app.ui.theme.Dimens

/**
 * The app's bottom sheet.
 *
 * Every sheet goes through here so they share one container, one drag handle, one set of insets and
 * one gutter. The Material defaults are kept deliberately — the M3 drag handle rather than a
 * hand-drawn bar (it carries the accessibility semantics and touch target a bare `Box` does not),
 * and [BedrudShapeTokens.sheetTop], which is already M3's 28dp `extraLarge` top corners, just named.
 *
 * [containerColor] is a parameter rather than fixed because the meeting chrome runs its own darker
 * overlay palette; everywhere else should take the default.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BedrudBottomSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    dragHandleColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = sheetState,
        shape = BedrudShapeTokens.sheetTop,
        containerColor = containerColor,
        dragHandle = {
            if (dragHandleColor != null) BottomSheetDefaults.DragHandle(color = dragHandleColor)
            else BottomSheetDefaults.DragHandle()
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
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
    trailing: @Composable (() -> Unit)? = null,
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        shape = BedrudShapeTokens.card,
        modifier = modifier.fillMaxWidth(),
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
