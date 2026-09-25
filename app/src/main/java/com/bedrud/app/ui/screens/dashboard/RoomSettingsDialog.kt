package com.bedrud.app.ui.screens.dashboard

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.bedrud.app.R
import com.bedrud.app.models.RoomSettings
import com.bedrud.app.models.UserRoomResponse
import com.bedrud.app.ui.components.BedrudButton
import com.bedrud.app.ui.components.BedrudButtonVariant
import com.bedrud.app.ui.components.RoomSettingsForm
import com.bedrud.app.ui.components.withFormEdits
import com.bedrud.app.ui.theme.typeCentered

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
    var allowChat by remember { mutableStateOf(room.settings.allowChat) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dashboard_roomSettings_title)) },
        text = {
            RoomSettingsForm(
                isPublic = isPublic,
                onIsPublicChange = { isPublic = it },
                allowChat = allowChat,
                onAllowChatChange = { allowChat = it },
                roomSettings = room.settings,
            )
        },
        confirmButton = {
            BedrudButton(
                text = stringResource(R.string.common_button_save),
                variant = BedrudButtonVariant.TONAL,
                onClick = { onSave(isPublic, room.settings.withFormEdits(allowChat = allowChat)) },
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
