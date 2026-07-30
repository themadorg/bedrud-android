package com.bedrud.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bedrud.app.R
import com.bedrud.app.models.RoomSettings

/**
 * The room-level settings toggles shared by the dashboard's settings dialog and the in-meeting
 * settings sheet — one place to add or unlock a toggle so the two surfaces can't drift.
 *
 * Public visibility is live; Require Approval, Recording, and E2EE are shown but locked off for
 * now — not ready to be user-controlled yet, tracked for a later pass. [contentColor] lets the
 * meeting sheet render labels on its chrome palette; Unspecified inherits the ambient color.
 */
@Composable
fun RoomSettingsForm(
    isPublic: Boolean,
    onIsPublicChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    contentColor: Color = Color.Unspecified,
    verticalSpacing: Dp = 4.dp,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(verticalSpacing)) {
        RoomSettingToggleRow(
            label = stringResource(R.string.dashboard_roomSettings_isPublic),
            checked = isPublic,
            contentColor = contentColor,
            onCheckedChange = onIsPublicChange,
        )
        RoomSettingToggleRow(
            label = stringResource(R.string.dashboard_roomSettings_requireApproval),
            checked = false,
            contentColor = contentColor,
            enabled = false,
            onCheckedChange = {},
        )
        RoomSettingToggleRow(
            label = stringResource(R.string.dashboard_roomSettings_recording),
            checked = false,
            contentColor = contentColor,
            enabled = false,
            onCheckedChange = {},
        )
        RoomSettingToggleRow(
            label = stringResource(R.string.dashboard_roomSettings_e2ee),
            checked = false,
            contentColor = contentColor,
            enabled = false,
            onCheckedChange = {},
        )
    }
}

/**
 * What both save paths submit alongside the form: the toggles the form shows locked are forced
 * to their locked values, and the media flags stay on (no UI for them yet). Must change together
 * with [RoomSettingsForm].
 */
fun RoomSettings.withLockedToggles(): RoomSettings = copy(
    allowChat = true,
    allowVideo = true,
    allowAudio = true,
    requireApproval = false,
    e2ee = false,
    recordingsAllowed = false,
)

@Composable
private fun RoomSettingToggleRow(
    label: String,
    checked: Boolean,
    contentColor: Color,
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
            color = contentColor,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
