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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.bedrud.app.R
import com.twilio.audioswitch.AudioDevice
import io.livekit.android.room.Room

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetingAudioSourceSheet(
    room: Room?,
    audioState: MeetingAudioState,
    isMicEnabled: Boolean,
    micHasError: Boolean = false,
    onDismiss: () -> Unit,
    onToggleMic: () -> Unit,
) {
    val colors = meetingChromeColors()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                text = stringResource(R.string.meeting_audio_sheet_title),
                color = colors.onButton,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 4.dp),
            )

            Surface(
                onClick = onToggleMic,
                shape = RoundedCornerShape(16.dp),
                color = if (isMicEnabled) colors.button else colors.buttonMediaOff,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = meetingAudioButtonIcon(isMicEnabled, audioState.selectedDevice),
                        contentDescription = null,
                        tint = if (isMicEnabled) colors.onButton else colors.onButtonMediaOff,
                        modifier = Modifier.size(22.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.meeting_audio_sheet_microphone),
                            color = if (isMicEnabled) colors.onButton else colors.onButtonMediaOff,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = if (isMicEnabled) {
                                stringResource(R.string.meeting_audio_sheet_micOn)
                            } else {
                                stringResource(R.string.meeting_audio_sheet_micOff)
                            },
                            color = colors.onButtonVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (micHasError) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .background(colors.warning, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "!",
                                color = colors.onWarning,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }

            Text(
                text = stringResource(R.string.meeting_audio_sheet_output),
                color = colors.onButtonVariant,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 4.dp),
            )

            if (audioState.availableDevices.isEmpty()) {
                Text(
                    text = stringResource(R.string.meeting_audio_sheet_noDevices),
                    color = colors.onButtonVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                audioState.availableDevices.forEach { device ->
                    val selected = audioState.selectedDevice?.let { current ->
                        current::class == device::class && current.name == device.name
                    } == true
                    AudioDeviceRow(
                        colors = colors,
                        device = device,
                        selected = selected,
                        onClick = {
                            audioState.selectDevice(room, device)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AudioDeviceRow(
    colors: MeetingChromeColors,
    device: AudioDevice,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) colors.selected else colors.button,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = audioDeviceIcon(device),
                contentDescription = null,
                tint = colors.onButton,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = audioDeviceLabel(device),
                color = colors.onButton,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}