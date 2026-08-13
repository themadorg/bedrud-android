package com.bedrud.app.ui.screens.meeting

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.bedrud.app.R
import com.bedrud.app.core.livekit.CallAudioSwitch
import com.bedrud.app.ui.components.BedrudBottomSheet
import com.bedrud.app.ui.components.BedrudSheetActionRow
import com.bedrud.app.ui.components.BedrudSheetTitle
import com.bedrud.app.ui.theme.Dimens

@Composable
fun MeetingAudioSourceSheet(
    audioHandler: CallAudioSwitch?,
    audioState: MeetingAudioState,
    onDismiss: () -> Unit,
) {
    val colors = meetingChromeColors()

    BedrudBottomSheet(onDismiss = onDismiss) {
        BedrudSheetTitle(
            text = stringResource(R.string.meeting_audio_sheet_title),
            color = colors.onButton,
        )

        if (audioState.availableDevices.isEmpty()) {
            Text(
                text = stringResource(R.string.meeting_audio_sheet_noDevices),
                color = colors.onButtonVariant,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(Dimens.space8),
            )
        } else {
            audioState.availableDevices.forEach { device ->
                val selected = audioState.selectedDevice?.let { current ->
                    current::class == device::class && current.name == device.name
                } == true
                BedrudSheetActionRow(
                    icon = audioDeviceIcon(device),
                    title = audioDeviceLabel(device),
                    // Selection is a trailing radio, per the design's output picker — the row
                    // stays unfilled, and the accent-tinted label backs the radio up.
                    contentColor = if (selected) colors.accent else colors.onButton,
                    trailing = {
                        RadioButton(
                            selected = selected,
                            onClick = { audioState.selectDevice(audioHandler, device) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = colors.accent,
                                unselectedColor = colors.onButtonVariant,
                            ),
                        )
                    },
                    onClick = { audioState.selectDevice(audioHandler, device) },
                )
            }
        }
    }
}
