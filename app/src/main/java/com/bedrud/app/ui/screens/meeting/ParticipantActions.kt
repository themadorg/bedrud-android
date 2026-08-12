package com.bedrud.app.ui.screens.meeting

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.bedrud.app.R
import com.bedrud.app.ui.components.ConfirmDialog
import kotlinx.coroutines.launch

/**
 * The long-press/three-dot menu shared by the participant tile and the participants panel row:
 * local mute and local video for everyone, kick/ban for admins. Kick asks for confirmation here
 * so both entry points behave the same; ban stays immediate.
 */
@Composable
internal fun ParticipantActionsMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    isAdmin: Boolean,
    isLocallyMuted: Boolean,
    onToggleLocalMute: () -> Unit,
    isVideoLocallyDisabled: Boolean,
    onToggleVideoDisabled: () -> Unit,
    onKickConfirmed: () -> Unit,
    onBan: () -> Unit,
) {
    var showKickConfirm by remember { mutableStateOf(false) }
    if (showKickConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.meeting_dialog_kickTitle),
            message = stringResource(R.string.meeting_dialog_kickMessage),
            confirmLabel = stringResource(R.string.meeting_action_kick),
            onConfirm = {
                showKickConfirm = false
                onKickConfirmed()
            },
            onDismiss = { showKickConfirm = false },
        )
    }
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = {
                Text(
                    stringResource(
                        if (isLocallyMuted) R.string.meeting_action_unmute
                        else R.string.meeting_action_mute
                    )
                )
            },
            onClick = {
                onDismiss()
                onToggleLocalMute()
            }
        )
        DropdownMenuItem(
            text = {
                Text(
                    stringResource(
                        if (isVideoLocallyDisabled) R.string.meeting_action_enableVideo
                        else R.string.meeting_action_disableVideo
                    )
                )
            },
            onClick = {
                onDismiss()
                onToggleVideoDisabled()
            }
        )
        if (isAdmin) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.meeting_action_kick), color = MaterialTheme.colorScheme.error) },
                onClick = {
                    onDismiss()
                    showKickConfirm = true
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.meeting_action_ban), color = MaterialTheme.colorScheme.error) },
                onClick = {
                    onDismiss()
                    onBan()
                }
            )
        }
    }
}

/** Fire-and-forget moderation call; a thrown failure surfaces as a snackbar with [fallbackMessage]. */
internal fun moderate(
    scope: kotlinx.coroutines.CoroutineScope?,
    snackbarHostState: SnackbarHostState?,
    fallbackMessage: String,
    action: suspend () -> Unit,
) {
    scope?.launch {
        try {
            action()
        } catch (e: Exception) {
            // Show the caller's localized fallback rather than the raw (untranslated) exception text.
            snackbarHostState?.showSnackbar(fallbackMessage)
        }
    }
}
