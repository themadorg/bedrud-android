package com.bedrud.app.ui.screens.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bedrud.app.R
import com.bedrud.app.models.RoomSettings
import com.bedrud.app.models.UserRoomResponse

@Composable
fun RoomSettingsDialog(
    room: UserRoomResponse,
    onDismiss: () -> Unit,
    onSave: (isPublic: Boolean, settings: RoomSettings) -> Unit
) {
    // Room's actual state is always present in the API response (no omitempty on the
    // server side); false is the safe fallback if it's ever missing rather than true,
    // since defaulting an unknown room to public would be the wrong direction to fail in.
    var isPublic by remember { mutableStateOf(room.isPublic ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dashboard_roomSettings_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SettingsToggleRow(stringResource(R.string.dashboard_roomSettings_isPublic), isPublic) { isPublic = it }
                // Require Approval, Recording and E2EE are shown but locked off for now --
                // not ready to be user-controlled yet, tracked for a later pass.
                SettingsToggleRow(
                    stringResource(R.string.dashboard_roomSettings_requireApproval),
                    checked = false,
                    enabled = false,
                    onCheckedChange = {},
                )
                SettingsToggleRow(
                    stringResource(R.string.dashboard_roomSettings_recording),
                    checked = false,
                    enabled = false,
                    onCheckedChange = {},
                )
                SettingsToggleRow(
                    stringResource(R.string.dashboard_roomSettings_e2ee),
                    checked = false,
                    enabled = false,
                    onCheckedChange = {},
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    isPublic,
                    room.settings.copy(
                        allowChat = true,
                        allowVideo = true,
                        allowAudio = true,
                        requireApproval = false,
                        e2ee = false,
                        recordingsAllowed = false,
                    )
                )
            }) {
                Text(stringResource(R.string.common_button_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_button_cancel))
            }
        }
    )
}

@Composable
private fun SettingsToggleRow(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
