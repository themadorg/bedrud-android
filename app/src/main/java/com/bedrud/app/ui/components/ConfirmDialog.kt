package com.bedrud.app.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.bedrud.app.R

/**
 * The app's destructive-confirmation dialog: title, message, a destructive filled confirm button,
 * and a plain cancel. Used for delete-room and kick-participant; anything needing a richer layout
 * (extra buttons, custom content) stays a bespoke AlertDialog.
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            BedrudButton(
                text = confirmLabel,
                variant = BedrudButtonVariant.DESTRUCTIVE,
                onClick = onConfirm,
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_button_cancel))
            }
        }
    )
}
