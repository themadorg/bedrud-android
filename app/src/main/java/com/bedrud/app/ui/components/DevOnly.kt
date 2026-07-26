package com.bedrud.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Construction
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bedrud.app.core.DevFlags
import com.bedrud.app.ui.theme.BedrudShapeTokens
import com.bedrud.app.ui.theme.Dimens

/**
 * Renders [content] only on dev/debug builds (gated by [DevFlags.hintsEnabled]); a no-op on
 * release. Use to surface UI that has no backing functionality yet, or developer/QA captions,
 * without leaking them to end users.
 */
@Composable
fun DevOnly(content: @Composable () -> Unit) {
    if (DevFlags.hintsEnabled) content()
}

/**
 * A small "not wired up yet" badge for UI whose backend/business logic doesn't exist yet.
 * Visible on dev builds only. Pairs an icon with a label so the meaning is never color-only.
 */
@Composable
fun DevHintBadge(
    text: String,
    modifier: Modifier = Modifier,
) {
    DevOnly {
        Surface(
            modifier = modifier,
            shape = BedrudShapeTokens.pill,
            color = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = Dimens.space8, vertical = Dimens.space4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.Construction,
                    contentDescription = null,
                    modifier = Modifier.size(Dimens.iconXs),
                )
                Spacer(Modifier.width(Dimens.space4))
                Text(text, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
