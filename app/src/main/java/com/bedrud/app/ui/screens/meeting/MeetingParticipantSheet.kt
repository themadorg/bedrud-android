package com.bedrud.app.ui.screens.meeting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.HeadsetOff
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.bedrud.app.R
import com.bedrud.app.core.api.RoomApi
import com.bedrud.app.ui.components.BedrudBottomSheet
import com.bedrud.app.ui.components.BedrudSheetActionRow
import com.bedrud.app.ui.components.BedrudSheetTitle
import com.bedrud.app.ui.components.ConfirmDialog
import com.bedrud.app.ui.components.DevHintBadge
import com.bedrud.app.ui.components.DevOnly
import com.bedrud.app.ui.theme.Dimens
import kotlinx.coroutines.launch

/**
 * Long-pressing a participant tile opens this sheet: local playback volume, the local mute /
 * don't-watch / pin / fullscreen controls, and — for room admins — the red moderation block.
 *
 * Room mute, room deafen and chat mute have no backing server endpoints yet, so they ship as
 * dev-only "coming soon" rows. TODO(#108): wire them once the moderation endpoints exist.
 */
@Composable
fun MeetingParticipantSheet(
    name: String,
    identity: String,
    isAdmin: Boolean,
    roomId: String,
    roomApi: RoomApi?,
    snackbarHostState: SnackbarHostState,
    scope: kotlinx.coroutines.CoroutineScope,
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    isLocallyMuted: Boolean,
    onToggleLocalMute: () -> Unit,
    isVideoLocallyDisabled: Boolean,
    onToggleVideoDisabled: () -> Unit,
    isPinned: Boolean,
    isDeafened: Boolean,
    onTogglePin: () -> Unit,
    onFullscreen: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = meetingChromeColors()
    val kickFailedMessage = stringResource(R.string.meeting_error_kickFailed)
    val banFailedMessage = stringResource(R.string.meeting_error_banFailed)
    var showKickConfirm by remember { mutableStateOf(false) }

    // The slider writes through immediately but keeps its own state so dragging stays smooth
    // even though the backing StateFlow only changes in coarse steps.
    var sliderValue by remember(identity) { mutableFloatStateOf(volume) }

    if (showKickConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.meeting_dialog_kickTitle),
            message = stringResource(R.string.meeting_dialog_kickMessage),
            confirmLabel = stringResource(R.string.meeting_action_kick),
            onConfirm = {
                showKickConfirm = false
                moderate(scope, snackbarHostState, kickFailedMessage) {
                    roomApi?.kickParticipant(roomId, identity)
                }
                onDismiss()
            },
            onDismiss = { showKickConfirm = false },
        )
    }

    BedrudBottomSheet(onDismiss = onDismiss) {
        BedrudSheetTitle(text = name, color = colors.onButton)
        // Worth saying here as well as on the tile: this is the sheet you open to change how you
        // hear somebody, and turning their volume up achieves nothing while they cannot hear you.
        if (isDeafened) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.space12, vertical = Dimens.space4),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.space8),
            ) {
                Icon(
                    imageVector = Icons.Default.HeadsetOff,
                    contentDescription = null,
                    tint = colors.onButtonVariant,
                    modifier = Modifier.size(Dimens.iconSm),
                )
                Text(
                    text = stringResource(R.string.meeting_participant_deafened),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onButtonVariant,
                )
            }
        }
        HorizontalDivider(color = colors.divider)

        // Playback volume, local to this viewer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.space12, vertical = Dimens.space4),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.space12),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.VolumeDown,
                contentDescription = null,
                tint = colors.onButtonVariant,
                modifier = Modifier.size(Dimens.iconMd),
            )
            MeetingCompactSlider(
                value = sliderValue,
                onValueChange = {
                    sliderValue = it
                    onVolumeChange(it)
                },
                label = stringResource(R.string.meeting_participant_volume),
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = null,
                tint = colors.onButtonVariant,
                modifier = Modifier.size(Dimens.iconMd),
            )
        }

        BedrudSheetActionRow(
            icon = if (isLocallyMuted) Icons.Default.Mic else Icons.Default.MicOff,
            title = stringResource(
                if (isLocallyMuted) R.string.meeting_action_unmute else R.string.meeting_action_mute
            ),
            contentColor = colors.onButton,
            onClick = {
                onToggleLocalMute()
                onDismiss()
            },
        )
        BedrudSheetActionRow(
            icon = if (isVideoLocallyDisabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
            title = stringResource(
                if (isVideoLocallyDisabled) R.string.meeting_action_watch
                else R.string.meeting_action_dontWatch
            ),
            contentColor = colors.onButton,
            onClick = {
                onToggleVideoDisabled()
                onDismiss()
            },
        )
        BedrudSheetActionRow(
            icon = Icons.Default.PushPin,
            title = stringResource(
                if (isPinned) R.string.meeting_action_unpin else R.string.meeting_action_pin
            ),
            contentColor = colors.onButton,
            onClick = {
                onTogglePin()
                onDismiss()
            },
        )
        BedrudSheetActionRow(
            icon = Icons.Default.Fullscreen,
            title = stringResource(R.string.meeting_contentDescription_tileFullscreen),
            contentColor = colors.onButton,
            onClick = {
                onFullscreen()
                onDismiss()
            },
        )

        if (isAdmin) {
            HorizontalDivider(color = colors.divider)

            // Server-backed moderation that exists today
            BedrudSheetActionRow(
                icon = Icons.Default.PersonRemove,
                title = stringResource(R.string.meeting_action_kick),
                contentColor = MaterialTheme.colorScheme.error,
                onClick = { showKickConfirm = true },
            )
            BedrudSheetActionRow(
                icon = Icons.Default.PersonOff,
                title = stringResource(R.string.meeting_action_ban),
                contentColor = MaterialTheme.colorScheme.error,
                onClick = {
                    moderate(scope, snackbarHostState, banFailedMessage) {
                        roomApi?.banParticipant(roomId, identity)
                    }
                    onDismiss()
                },
            )

            // Moderation that still needs server endpoints — dev builds only (#108)
            DevOnly {
                BedrudSheetActionRow(
                    icon = Icons.Default.MicOff,
                    title = stringResource(R.string.meeting_action_roomMute),
                    contentColor = MaterialTheme.colorScheme.error,
                    trailing = { DevHintBadge(stringResource(R.string.common_hint_comingSoon)) },
                    onClick = {},
                )
                BedrudSheetActionRow(
                    icon = Icons.Default.HeadsetOff,
                    title = stringResource(R.string.meeting_action_roomDeafen),
                    contentColor = MaterialTheme.colorScheme.error,
                    trailing = { DevHintBadge(stringResource(R.string.common_hint_comingSoon)) },
                    onClick = {},
                )
                BedrudSheetActionRow(
                    icon = Icons.AutoMirrored.Filled.Chat,
                    title = stringResource(R.string.meeting_action_chatMute),
                    contentColor = MaterialTheme.colorScheme.error,
                    trailing = { DevHintBadge(stringResource(R.string.common_hint_comingSoon)) },
                    onClick = {},
                )
            }
        }
    }
}

