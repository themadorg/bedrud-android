package com.bedrud.app.ui.screens.meeting

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bedrud.app.R
import com.twilio.audioswitch.AudioDevice

@Composable
fun MeetingAudioButton(
    isMicEnabled: Boolean,
    selectedDevice: AudioDevice?,
    hasError: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = meetingChromeColors()

    Box(modifier = modifier) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = colors.bar,
            border = BorderStroke(1.dp, colors.divider),
            shadowElevation = 2.dp,
            modifier = Modifier.size(40.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = meetingAudioButtonIcon(isMicEnabled, selectedDevice),
                    contentDescription = stringResource(R.string.meeting_contentDescription_audioSource),
                    tint = if (isMicEnabled) colors.onButton else colors.onButtonMediaOff,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        if (hasError) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = (-2).dp)
                    .size(14.dp)
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