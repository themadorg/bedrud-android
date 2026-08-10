package com.bedrud.app.ui.screens.meeting

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.bedrud.app.R
import com.bedrud.app.core.api.RoomApi
import com.bedrud.app.core.api.apiAction
import com.bedrud.app.models.RoomSettings
import com.bedrud.app.models.UpdateRoomSettingsRequest
import com.bedrud.app.ui.components.BedrudBottomSheet
import com.bedrud.app.ui.components.BedrudSheetTitle
import com.bedrud.app.ui.components.RoomSettingsForm
import com.bedrud.app.ui.components.withLockedToggles
import com.bedrud.app.ui.theme.Dimens
import kotlinx.coroutines.launch

// In-room mirror of RoomSettingsDialog (dashboard) — same three room-level toggles,
// same PUT /room/{roomId}/settings endpoint, so a room's visibility/approval/E2EE can
// be managed without leaving the call.
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
    val scope = rememberCoroutineScope()

    var localIsPublic by remember { mutableStateOf(isPublic) }
    var isSaving by remember { mutableStateOf(false) }

    BedrudBottomSheet(onDismiss = onDismiss) {
        BedrudSheetTitle(
            text = stringResource(R.string.dashboard_roomSettings_title),
            color = colors.onButton,
        )

        // No verticalSpacing override: the form should read the same here as it does in the
        // dashboard's room-settings dialog.
        RoomSettingsForm(
            isPublic = localIsPublic,
            onIsPublicChange = { localIsPublic = it },
            contentColor = colors.onButton,
        )

        Button(
            onClick = {
                if (isSaving) return@Button
                val newSettings = settings.withLockedToggles()
                isSaving = true
                scope.launch {
                    try {
                        val saved = apiAction("Failed to save settings", { snackbarHostState.showSnackbar(it) }) {
                            roomApi.updateRoomSettings(
                                roomId,
                                UpdateRoomSettingsRequest(isPublic = localIsPublic, settings = newSettings),
                            )
                        }
                        if (saved) {
                            onSaved(localIsPublic, newSettings)
                            onDismiss()
                        }
                    } finally {
                        isSaving = false
                    }
                }
            },
            enabled = !isSaving,
            colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Dimens.space4),
        ) {
            Text(stringResource(R.string.common_button_save))
        }
    }
}
