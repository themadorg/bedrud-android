package com.bedrud.app.ui.screens.meeting

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bedrud.app.R
import com.bedrud.app.core.api.RoomApi
import com.bedrud.app.models.RoomSettings
import com.bedrud.app.models.UpdateRoomSettingsRequest
import kotlinx.coroutines.launch

// In-room mirror of RoomSettingsDialog (dashboard) — same three room-level toggles,
// same PUT /room/{roomId}/settings endpoint, so a room's visibility/approval/E2EE can
// be managed without leaving the call.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetingRoomSettingsSheet(
    roomId: String,
    roomApi: RoomApi,
    isPublic: Boolean,
    settings: RoomSettings,
    snackbarHostState: SnackbarHostState,
    onDismiss: () -> Unit,
    onSaved: (isPublic: Boolean, settings: RoomSettings) -> Unit,
) {
    val colors = meetingChromeColors()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    var localIsPublic by remember { mutableStateOf(isPublic) }
    var isSaving by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.sheet,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .width(32.dp)
                    .height(4.dp)
                    .background(colors.dragHandle, RoundedCornerShape(2.dp)),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.dashboard_roomSettings_title),
                color = colors.onButton,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 4.dp),
            )

            MeetingSettingsToggleRow(
                colors = colors,
                label = stringResource(R.string.dashboard_roomSettings_isPublic),
                checked = localIsPublic,
                onCheckedChange = { localIsPublic = it },
            )
            // Require Approval, Recording and E2EE are shown but locked off for now --
            // not ready to be user-controlled yet, tracked for a later pass.
            MeetingSettingsToggleRow(
                colors = colors,
                label = stringResource(R.string.dashboard_roomSettings_requireApproval),
                checked = false,
                enabled = false,
                onCheckedChange = {},
            )
            MeetingSettingsToggleRow(
                colors = colors,
                label = stringResource(R.string.dashboard_roomSettings_recording),
                checked = false,
                enabled = false,
                onCheckedChange = {},
            )
            MeetingSettingsToggleRow(
                colors = colors,
                label = stringResource(R.string.dashboard_roomSettings_e2ee),
                checked = false,
                enabled = false,
                onCheckedChange = {},
            )

            Button(
                onClick = {
                    if (isSaving) return@Button
                    val newSettings = settings.copy(
                        allowChat = true,
                        allowVideo = true,
                        allowAudio = true,
                        requireApproval = false,
                        e2ee = false,
                        recordingsAllowed = false,
                    )
                    isSaving = true
                    scope.launch {
                        try {
                            val response = roomApi.updateRoomSettings(
                                roomId,
                                UpdateRoomSettingsRequest(isPublic = localIsPublic, settings = newSettings),
                            )
                            if (response.isSuccessful) {
                                onSaved(localIsPublic, newSettings)
                                onDismiss()
                            } else {
                                snackbarHostState.showSnackbar("Failed to save settings")
                            }
                        } catch (e: Exception) {
                            snackbarHostState.showSnackbar(e.message ?: "Failed to save settings")
                        } finally {
                            isSaving = false
                        }
                    }
                },
                enabled = !isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            ) {
                Text(stringResource(R.string.common_button_save))
            }
        }
    }
}

@Composable
private fun MeetingSettingsToggleRow(
    colors: MeetingChromeColors,
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
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = colors.onButton,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
