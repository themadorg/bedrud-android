package com.bedrud.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.bedrud.app.ui.theme.Dimens

/**
 * Scaffold content insets without IME. [android:windowSoftInputMode=adjustResize] already
 * shrinks the window — padding IME again leaves a large empty gap above the keyboard.
 */
val BedrudScaffoldContentInsets: WindowInsets
    @Composable get() = WindowInsets.safeDrawing.exclude(WindowInsets.ime)

/** Main tab shell — horizontal cutouts only; bottom nav handles the lower edge. */
val BedrudMainScaffoldContentInsets: WindowInsets
    @Composable get() = WindowInsets.safeDrawing
        .only(WindowInsetsSides.Horizontal)
        .exclude(WindowInsets.ime)

/** Tab Scaffolds — top inset is handled by [BedrudCompactTopBar]. */
val BedrudTabScaffoldContentInsets: WindowInsets
    @Composable get() = WindowInsets.safeDrawing
        .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
        .exclude(WindowInsets.ime)

@Composable
fun BedrudCompactTopBar(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    BedrudCompactTopBar(
        modifier = modifier,
        actions = actions,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

/**
 * Slot-based variant: the caller supplies the title content, so pages that need a richer title
 * (e.g. the rooms header's two-tone, server-colored name) can render their own composable while
 * keeping the shared status-bar padding, height, and actions layout.
 */
@Composable
fun BedrudCompactTopBar(
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    title: @Composable () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(
                    start = Dimens.space16,
                    end = Dimens.space4,
                    top = Dimens.space2,
                    bottom = Dimens.space2,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f)) { title() }
            actions()
        }
    }
}

/** Compact icon button for top bar / panel headers. */
@Composable
fun BedrudCompactIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    androidx.compose.material3.IconButton(
        onClick = onClick,
        modifier = modifier.size(Dimens.avatar),
        content = content,
    )
}
