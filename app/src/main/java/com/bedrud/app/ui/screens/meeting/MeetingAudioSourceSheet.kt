package com.bedrud.app.ui.screens.meeting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.bedrud.app.R
import com.bedrud.app.ui.theme.BedrudShapeTokens
import com.bedrud.app.core.livekit.CallAudioSwitch
import com.twilio.audioswitch.AudioDevice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetingAudioSourceSheet(
    audioHandler: CallAudioSwitch?,
    audioState: MeetingAudioState,
    onDismiss: () -> Unit,
) {
    val colors = meetingChromeColors()

    MeetingBottomSheet(onDismiss = onDismiss) {
        Text(
            text = stringResource(R.string.meeting_audio_sheet_title),
            color = colors.onButton,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 4.dp),
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
                        audioState.selectDevice(audioHandler, device)
                    },
                )
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
        shape = BedrudShapeTokens.card,
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