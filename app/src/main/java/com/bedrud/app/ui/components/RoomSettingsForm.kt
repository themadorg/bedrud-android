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
import com.bedrud.app.ui.theme.Dimens

/**
 * The room-level settings toggles shared by the dashboard's settings dialog and the in-meeting
 * settings sheet — one place to add or unlock a toggle so the two surfaces can't drift.
 *
 * Public visibility and chat are live; Require Approval, Recording, and E2EE are shown but
 * locked — not ready to be user-controlled yet, tracked for a later pass. A locked toggle shows
 * the room's own value from [roomSettings], so a room with recording allowed on the web says
 * so here rather than reading as off. The live toggles come first so the locked ones sit
 * together below them. [contentColor] lets the meeting sheet render labels on its chrome
 * palette; Unspecified inherits the ambient color.
 */
@Composable
fun RoomSettingsForm(
    isPublic: Boolean,
    onIsPublicChange: (Boolean) -> Unit,
    allowChat: Boolean,
    onAllowChatChange: (Boolean) -> Unit,
    roomSettings: RoomSettings,
    modifier: Modifier = Modifier,
    contentColor: Color = Color.Unspecified,
    verticalSpacing: Dp = Dimens.space4,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(verticalSpacing)) {
        RoomSettingToggleRow(
            label = stringResource(R.string.dashboard_roomSettings_isPublic),
            checked = isPublic,
            contentColor = contentColor,
            onCheckedChange = onIsPublicChange,
        )
        RoomSettingToggleRow(
            label = stringResource(R.string.dashboard_roomSettings_allowChat),
            checked = allowChat,
            contentColor = contentColor,
            onCheckedChange = onAllowChatChange,
        )
        RoomSettingToggleRow(
            label = stringResource(R.string.dashboard_roomSettings_requireApproval),
            checked = roomSettings.requireApproval,
            contentColor = contentColor,
            enabled = false,
            onCheckedChange = {},
        )
        RoomSettingToggleRow(
            label = stringResource(R.string.dashboard_roomSettings_recording),
            checked = roomSettings.recordingsAllowed,
            contentColor = contentColor,
            enabled = false,
            onCheckedChange = {},
        )
        RoomSettingToggleRow(
            label = stringResource(R.string.dashboard_roomSettings_e2ee),
            checked = roomSettings.e2ee,
            contentColor = contentColor,
            enabled = false,
            onCheckedChange = {},
        )
    }
}

/**
 * What both save paths submit: the room's settings with the form's edits applied. Everything the
 * form does not edit goes back exactly as the server reported it — the locked toggles, the media
 * flags, persistence — because a save from Android must not undo a choice made elsewhere, such
 * as recording allowed or approval required from the web. Must change together with
 * [RoomSettingsForm].
 */
fun RoomSettings.withFormEdits(allowChat: Boolean): RoomSettings = copy(allowChat = allowChat)

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
