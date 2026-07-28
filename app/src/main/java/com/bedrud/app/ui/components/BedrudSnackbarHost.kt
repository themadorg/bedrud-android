package com.bedrud.app.ui.components

import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bedrud.app.ui.theme.BedrudShapeTokens

/**
 * App-wide snackbar host.
 *
 * Renders the standard Material 3 [Snackbar] — keeping its theme-aware inverse colors, action
 * button, and dismiss handling from the active `ColorScheme` — but with the Bedrud rounded shape
 * token so it matches the fields and buttons instead of the default 4dp corners.
 */
@Composable
fun BedrudSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    SnackbarHost(hostState, modifier) { data ->
        Snackbar(
            snackbarData = data,
            shape = BedrudShapeTokens.snackbar
        )
    }
}
