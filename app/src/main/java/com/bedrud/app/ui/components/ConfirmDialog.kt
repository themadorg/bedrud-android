package com.bedrud.app.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.bedrud.app.R
import com.bedrud.app.ui.theme.typeCentered

/**
 * The app's confirmation dialog: title, message, a filled confirm button, and a plain cancel. Used
 * for delete-room and kick-participant; anything needing a richer layout (extra buttons, custom
 * content) stays a bespoke AlertDialog.
 *
 * The confirm is destructive by default, since most of what needs confirming cannot be undone. A
 * choice that can be — leaving one call for another, which the reader can rejoin — passes
 * [confirmVariant] instead: the destructive colour is kept for what is actually lost.
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmVariant: BedrudButtonVariant = BedrudButtonVariant.DESTRUCTIVE,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            BedrudButton(
                text = confirmLabel,
                variant = confirmVariant,
                onClick = onConfirm,
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                // Corrected like the BedrudButton beside it, or the two labels in this dialog sit
                // at different heights.
                val labelStyle = LocalTextStyle.current
                Text(
                    stringResource(R.string.common_button_cancel),
                    modifier = Modifier.typeCentered(labelStyle),
                )
            }
        }
    )
}
