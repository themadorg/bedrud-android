package com.bedrud.app.ui.screens.meeting

import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.bedrud.app.R
import com.bedrud.app.core.audio.MeetingInputMode
import com.bedrud.app.core.audio.NoiseSuppressionMode
import com.bedrud.app.core.livekit.CallAudioSwitch
import com.bedrud.app.ui.components.BedrudSheetActionRow
import com.bedrud.app.ui.components.BedrudSheetTitle
import com.bedrud.app.ui.theme.Dimens

/**
 * The full in-call audio sheet: output device, output volume (the voice-call stream the hardware
 * keys also drive), the input mode, and — in voice-activity mode — the sensitivity controls.
 * Sensitivity is only adjustable with auto off; auto keeps the platform's own processing, which
 * is also today's default behavior.
 */
@Composable
fun MeetingAudioSettingsSheet(
    audioHandler: CallAudioSwitch?,
    audioState: MeetingAudioState,
    inputMode: MeetingInputMode,
    autoSensitivity: Boolean,
    sensitivity: Float,
    onOpenInputModePicker: () -> Unit,
    onAutoSensitivityChange: (Boolean) -> Unit,
    onSensitivityChange: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = meetingChromeColors()
    val context = LocalContext.current
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val maxVolume = remember(audioManager) {
        audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL).coerceAtLeast(1)
    }
    var outputVolume by remember {
        mutableFloatStateOf(
            audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL).toFloat() / maxVolume
        )
    }
    var sensitivityValue by remember { mutableFloatStateOf(sensitivity) }

    MeetingBottomSheet(onDismiss = onDismiss) {
        BedrudSheetTitle(
            text = stringResource(R.string.meeting_sheet_audioSettings),
            color = colors.onButton,
        )

        // Output device
        Text(
            text = stringResource(R.string.meeting_audio_sheet_title),
            style = MaterialTheme.typography.labelLarge,
            color = colors.onButtonVariant,
            modifier = Modifier.padding(horizontal = Dimens.space4, vertical = Dimens.space4),
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

        HorizontalDivider(color = colors.divider)

        // Output volume — writes through to the voice-call stream
        Text(
            text = stringResource(R.string.meeting_audio_outputVolume),
            style = MaterialTheme.typography.labelLarge,
            color = colors.onButtonVariant,
            modifier = Modifier.padding(horizontal = Dimens.space4, vertical = Dimens.space4),
        )
        VolumeSliderRow(
            value = outputVolume,
            label = stringResource(R.string.meeting_audio_outputVolume),
            iconTint = colors.onButtonVariant,
            onValueChange = { value ->
                outputVolume = value
                audioManager.setStreamVolume(
                    AudioManager.STREAM_VOICE_CALL,
                    (value * maxVolume).toInt().coerceIn(0, maxVolume),
                    0,
                )
            },
        )

        HorizontalDivider(color = colors.divider)

        // Input mode + sensitivity
        BedrudSheetActionRow(
            icon = Icons.Default.Mic,
            title = stringResource(R.string.meeting_audio_inputMode),
            supportingText = stringResource(
                when (inputMode) {
                    MeetingInputMode.VOICE_ACTIVITY -> R.string.meeting_audio_mode_voiceActivity
                    MeetingInputMode.PUSH_TO_TALK -> R.string.meeting_audio_mode_pushToTalk
                }
            ),
            contentColor = colors.onButton,
            supportingColor = colors.accent,
            onClick = onOpenInputModePicker,
        )

        if (inputMode == MeetingInputMode.VOICE_ACTIVITY) {
            BedrudSheetActionRow(
                icon = Icons.Default.GraphicEq,
                title = stringResource(R.string.meeting_audio_autoSensitivity),
                contentColor = colors.onButton,
                trailing = {
                    Switch(
                        checked = autoSensitivity,
                        onCheckedChange = onAutoSensitivityChange,
                        colors = SwitchDefaults.colors(checkedTrackColor = colors.accent),
                    )
                },
                onClick = { onAutoSensitivityChange(!autoSensitivity) },
            )

            if (!autoSensitivity) {
                Text(
                    text = stringResource(R.string.meeting_audio_sensitivity),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onButtonVariant,
                    modifier = Modifier.padding(horizontal = Dimens.space4, vertical = Dimens.space4),
                )
                VolumeSliderRow(
                    value = sensitivityValue,
                    label = stringResource(R.string.meeting_audio_sensitivity),
                    iconTint = colors.onButtonVariant,
                    onValueChange = { value ->
                        sensitivityValue = value
                        onSensitivityChange(value)
                    },
                )
            }
        }
    }
}

@Composable
private fun VolumeSliderRow(
    value: Float,
    label: String,
    iconTint: androidx.compose.ui.graphics.Color,
    onValueChange: (Float) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.space12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.space12),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.VolumeDown,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(Dimens.iconMd),
        )
        MeetingCompactSlider(
            value = value,
            onValueChange = onValueChange,
            label = label,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(Dimens.iconMd),
        )
    }
}

/** The input-mode picker: push to talk or voice activity, per the design's inner sheet. */
@Composable
fun MeetingInputModeSheet(
    inputMode: MeetingInputMode,
    onSelect: (MeetingInputMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = meetingChromeColors()

    MeetingBottomSheet(onDismiss = onDismiss) {
        BedrudSheetTitle(
            text = stringResource(R.string.meeting_audio_inputMode),
            color = colors.onButton,
        )
        ModeRow(
            title = stringResource(R.string.meeting_audio_mode_pushToTalk),
            icon = Icons.Default.TouchApp,
            selected = inputMode == MeetingInputMode.PUSH_TO_TALK,
            colors = colors,
            onClick = {
                onSelect(MeetingInputMode.PUSH_TO_TALK)
                onDismiss()
            },
        )
        ModeRow(
            title = stringResource(R.string.meeting_audio_mode_voiceActivity),
            icon = Icons.Default.GraphicEq,
            selected = inputMode == MeetingInputMode.VOICE_ACTIVITY,
            colors = colors,
            onClick = {
                onSelect(MeetingInputMode.VOICE_ACTIVITY)
                onDismiss()
            },
        )
    }
}

/**
 * Noise-suppression picker. Off/Device apply on the next join (the audio device module is built
 * per connection); the richer modes stay dev-hinted until #106.
 */
@Composable
fun MeetingNoiseSuppressionSheet(
    mode: NoiseSuppressionMode,
    onSelect: (NoiseSuppressionMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = meetingChromeColors()

    MeetingBottomSheet(onDismiss = onDismiss) {
        BedrudSheetTitle(
            text = stringResource(R.string.meeting_sheet_noiseSuppression),
            color = colors.onButton,
        )
        Text(
            text = stringResource(R.string.meeting_ns_appliesNextJoin),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onButtonVariant,
            modifier = Modifier.padding(horizontal = Dimens.space4),
        )
        ModeRow(
            title = stringResource(R.string.meeting_ns_off),
            icon = Icons.Default.Block,
            selected = mode == NoiseSuppressionMode.OFF,
            colors = colors,
            onClick = {
                onSelect(NoiseSuppressionMode.OFF)
                onDismiss()
            },
        )
        ModeRow(
            title = stringResource(R.string.meeting_ns_device),
            icon = Icons.Default.PhoneAndroid,
            selected = mode == NoiseSuppressionMode.DEVICE,
            colors = colors,
            onClick = {
                onSelect(NoiseSuppressionMode.DEVICE)
                onDismiss()
            },
        )
    }
}

@Composable
private fun ModeRow(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    colors: MeetingChromeColors,
    onClick: () -> Unit,
) {
    BedrudSheetActionRow(
        icon = icon,
        title = title,
        contentColor = if (selected) colors.accent else colors.onButton,
        trailing = {
            RadioButton(
                selected = selected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = colors.accent,
                    unselectedColor = colors.onButtonVariant,
                ),
            )
        },
        onClick = onClick,
    )
}
