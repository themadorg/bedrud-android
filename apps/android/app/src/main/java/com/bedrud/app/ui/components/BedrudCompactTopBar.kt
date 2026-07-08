package com.bedrud.app.ui.components

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
import androidx.compose.ui.unit.dp

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
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
        modifier = modifier.size(40.dp),
        content = content,
    )
}