package com.bedrud.app.ui.screens.meeting

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.bedrud.app.R
import com.bedrud.app.core.livekit.CallAudioSwitch
import com.twilio.audioswitch.AudioDevice
import com.twilio.audioswitch.AudioDeviceChangeListener

data class MeetingAudioState(
    val availableDevices: List<AudioDevice> = emptyList(),
    val selectedDevice: AudioDevice? = null,
)

@Composable
fun rememberMeetingAudioState(audioHandler: CallAudioSwitch?): MeetingAudioState {
    var availableDevices by remember { mutableStateOf(emptyList<AudioDevice>()) }
    var selectedDevice by remember { mutableStateOf<AudioDevice?>(null) }

    DisposableEffect(audioHandler) {
        val listener: AudioDeviceChangeListener = { devices, selected ->
            availableDevices = devices.sortedForDisplay()
            selectedDevice = selected
        }
        audioHandler?.registerAudioDeviceChangeListener(listener)
        availableDevices = audioHandler?.availableAudioDevices.orEmpty().sortedForDisplay()
        selectedDevice = audioHandler?.selectedAudioDevice

        onDispose {
            audioHandler?.unregisterAudioDeviceChangeListener(listener)
        }
    }

    return MeetingAudioState(
        availableDevices = availableDevices,
        selectedDevice = selectedDevice,
    )
}

fun MeetingAudioState.selectDevice(audioHandler: CallAudioSwitch?, device: AudioDevice) {
    audioHandler?.selectDevice(device)
}

/**
 * Fixed list order for the audio output picker: Phone, then Speaker, then everything else
 * (wired/Bluetooth devices), independent of which device is currently preferred/selected.
 */
private fun List<AudioDevice>.sortedForDisplay(): List<AudioDevice> = sortedBy { device ->
    when (device) {
        is AudioDevice.Earpiece -> 0
        is AudioDevice.Speakerphone -> 1
        else -> 2
    }
}

@Composable
fun audioDeviceLabel(device: AudioDevice): String {
    val speakerLabel = stringResource(R.string.meeting_audio_device_speaker)
    val fallback = device.name
        .replace(Regex("(?i)speeker"), speakerLabel)
        .replace(Regex("(?i)speakerphone"), speakerLabel)
    return when (device) {
        is AudioDevice.Speakerphone -> stringResource(R.string.meeting_audio_device_speaker)
        is AudioDevice.Earpiece -> stringResource(R.string.meeting_audio_device_earpiece)
        is AudioDevice.WiredHeadset -> stringResource(R.string.meeting_audio_device_wired_headset)
        is AudioDevice.BluetoothHeadset -> fallback.ifBlank {
            stringResource(R.string.meeting_audio_device_bluetooth)
        }
        else -> fallback
    }
}

fun audioDeviceIcon(device: AudioDevice): ImageVector =
    when (device) {
        is AudioDevice.BluetoothHeadset -> Icons.Default.Bluetooth
        is AudioDevice.WiredHeadset -> Icons.Default.Headphones
        is AudioDevice.Speakerphone -> Icons.Default.VolumeUp
        is AudioDevice.Earpiece -> Icons.Default.Phone
        else -> Icons.Default.VolumeUp
    }

fun meetingAudioButtonIcon(isMicEnabled: Boolean, selectedDevice: AudioDevice?): ImageVector {
    if (!isMicEnabled) return Icons.Default.MicOff
    return selectedDevice?.let(::audioDeviceIcon) ?: Icons.Default.Mic
}