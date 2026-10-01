package com.bedrud.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.bedrud.app.R
import com.bedrud.app.models.RoomSettings
import com.bedrud.app.ui.theme.Dimens

/**
 * A toggle the app cannot change yet. Its badge sits under the label, so it takes none of the
 * label's width beside the switch.
 */
@Composable
private fun LockedSwitchRow(
    label: String,
    checked: Boolean,
    contentColor: Color,
) {
    BedrudSwitchRow(
        label = label,
        checked = checked,
        onCheckedChange = {},
        enabled = false,
        contentColor = contentColor,
        supportingContent = { DevHintBadge(stringResource(R.string.common_hint_comingSoon)) },
    )
}

/**
 * The room-level settings toggles shared by the dashboard's settings dialog and the in-meeting
 * settings sheet — one place to add or unlock a toggle so the two surfaces can't drift.
 *
 * Public visibility and chat are live; Require Approval, Recording, and E2EE are shown but
 * locked — not ready to be user-controlled yet, tracked for a later pass — and carry the dev-build
 * "coming soon" badge every other unwired control does. A locked toggle shows the room's own value
 * from [roomSettings], so a room with recording allowed on the web says so here rather than
 * reading as off. The live toggles come first so the locked ones sit together below them.
 * [contentColor] lets the meeting sheet render labels on its chrome palette; Unspecified inherits
 * the ambient color.
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
        BedrudSwitchRow(
            label = stringResource(R.string.dashboard_roomSettings_isPublic),
            checked = isPublic,
            contentColor = contentColor,
            onCheckedChange = onIsPublicChange,
        )
        BedrudSwitchRow(
            label = stringResource(R.string.dashboard_roomSettings_allowChat),
            checked = allowChat,
            contentColor = contentColor,
            onCheckedChange = onAllowChatChange,
        )
        LockedSwitchRow(
            label = stringResource(R.string.dashboard_roomSettings_requireApproval),
            checked = roomSettings.requireApproval,
            contentColor = contentColor,
        )
        LockedSwitchRow(
            label = stringResource(R.string.dashboard_roomSettings_recording),
            checked = roomSettings.recordingsAllowed,
            contentColor = contentColor,
        )
        LockedSwitchRow(
            label = stringResource(R.string.dashboard_roomSettings_e2ee),
            checked = roomSettings.e2ee,
            contentColor = contentColor,
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
