package com.bedrud.app.ui.screens.meeting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bedrud.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetingMoreOptionsSheet(
    isCameraEnabled: Boolean,
    isDeafened: Boolean,
    unreadCount: Int,
    onDismiss: () -> Unit,
    onSwitchCamera: () -> Unit,
    onToggleChat: () -> Unit,
    onToggleParticipants: () -> Unit,
    onCopyRoomLink: () -> Unit,
    onToggleDeafen: () -> Unit,
    onOpenAudioSettings: () -> Unit,
) {
    val colors = meetingChromeColors()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.sheet,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            ) {
                if (isCameraEnabled) {
                    SheetCircleAction(
                        colors = colors,
                        icon = Icons.Default.Cameraswitch,
                        contentDescription = stringResource(R.string.meeting_contentDescription_switchCamera),
                        onClick = {
                            onSwitchCamera()
                            onDismiss()
                        },
                    )
                }
                SheetCircleAction(
                    colors = colors,
                    icon = Icons.Default.People,
                    contentDescription = stringResource(R.string.meeting_contentDescription_participants),
                    onClick = {
                        onToggleParticipants()
                        onDismiss()
                    },
                )
                SheetCircleAction(
                    colors = colors,
                    icon = Icons.Default.PersonAdd,
                    contentDescription = stringResource(R.string.meeting_contentDescription_copyRoomLink),
                    onClick = {
                        onCopyRoomLink()
                        onDismiss()
                    },
                )
            }

            SheetLabeledButton(
                colors = colors,
                icon = Icons.AutoMirrored.Filled.Chat,
                label = stringResource(R.string.meeting_sheet_chat),
                badge = if (unreadCount > 0) {
                    if (unreadCount > 9) "9+" else unreadCount.toString()
                } else {
                    null
                },
                onClick = {
                    onToggleChat()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            )

            SheetLabeledButton(
                colors = colors,
                icon = if (isDeafened) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                label = stringResource(R.string.meeting_sheet_deafen),
                active = isDeafened,
                onClick = {
                    onToggleDeafen()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            )

            SheetLabeledButton(
                colors = colors,
                icon = Icons.Default.Settings,
                label = stringResource(R.string.meeting_sheet_settings),
                onClick = {
                    onDismiss()
                    onOpenAudioSettings()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SheetCircleAction(
    colors: MeetingChromeColors,
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = colors.button,
        modifier = Modifier.size(64.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = colors.onButton,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun SheetLabeledButton(
    colors: MeetingChromeColors,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
    enabled: Boolean = true,
    active: Boolean = false,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        color = if (active) colors.buttonActive else colors.button,
        modifier = modifier.height(72.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (badge != null) {
                BadgedBox(
                    badge = { Badge { Text(badge) } },
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = colors.onButton,
                        modifier = Modifier.size(22.dp),
                    )
                }
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) colors.onButton else colors.onButtonVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
            Text(
                text = label,
                color = if (enabled) colors.onButton else colors.onButtonVariant,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}