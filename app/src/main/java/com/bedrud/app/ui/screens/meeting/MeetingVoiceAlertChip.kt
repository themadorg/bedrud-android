package com.bedrud.app.ui.screens.meeting

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.bedrud.app.R
import com.bedrud.app.core.audio.MeetingVoiceAlert
import com.bedrud.app.ui.theme.BedrudShapeTokens
import com.bedrud.app.ui.theme.Dimens
import com.bedrud.app.ui.theme.bedrudColors

/**
 * The chip that speaks up when the room is not hearing someone who is plainly talking. It sits
 * just above the controls bar, where the mic button it is talking about already is.
 *
 * Only [MeetingVoiceAlert.NotReachingRoom] is dressed as a caution, in the amber `warning` role
 * rather than error red: audio that should be arriving and is not is worth interrupting for, but
 * nothing here is an error or irreversible. Being muted, or holding push-to-talk wrong, is
 * ordinary and fixed in one tap, so those stay in the plain chrome colours — dressing them louder
 * would cry wolf and teach people to ignore the chip.
 */
@Composable
fun MeetingVoiceAlertChip(
    alert: MeetingVoiceAlert,
    onOpenAudioSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The chip fades out rather than vanishing, so it needs the wording of the alert that is
    // leaving, not of the None that replaced it.
    var shown by remember { mutableStateOf(alert) }
    LaunchedEffect(alert) {
        if (alert != MeetingVoiceAlert.None) shown = alert
    }

    AnimatedVisibility(
        visible = alert != MeetingVoiceAlert.None,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        val colors = meetingChromeColors()
        val isCaution = shown == MeetingVoiceAlert.NotReachingRoom
        val container = if (isCaution) MaterialTheme.bedrudColors.warning else colors.bar
        val content = if (isCaution) MaterialTheme.bedrudColors.onWarning else colors.onButton
        // Only the two settings-shaped causes have somewhere to go; the rest are already fixable
        // from the bar right below the chip.
        val opensSettings = shown == MeetingVoiceAlert.GateClosed ||
            shown == MeetingVoiceAlert.NotReachingRoom

        Row(
            modifier = Modifier
                .background(container, BedrudShapeTokens.chip)
                .then(if (opensSettings) Modifier.clickable(onClick = onOpenAudioSettings) else Modifier)
                .padding(horizontal = Dimens.space12, vertical = Dimens.space8),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.space8),
        ) {
            Icon(
                imageVector = shown.icon(),
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(Dimens.iconSm),
            )
            Text(
                text = stringResource(shown.messageRes()),
                style = MaterialTheme.typography.labelLarge,
                color = content,
            )
            if (opensSettings) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(Dimens.iconSm),
                )
            }
        }
    }
}

private fun MeetingVoiceAlert.icon(): ImageVector = when (this) {
    MeetingVoiceAlert.Muted -> Icons.Default.MicOff
    MeetingVoiceAlert.PushToTalkIdle -> Icons.Default.TouchApp
    MeetingVoiceAlert.GateClosed -> Icons.Default.GraphicEq
    MeetingVoiceAlert.NotReachingRoom, MeetingVoiceAlert.None -> Icons.Default.WarningAmber
}

private fun MeetingVoiceAlert.messageRes(): Int = when (this) {
    MeetingVoiceAlert.Muted -> R.string.meeting_voiceAlert_muted
    MeetingVoiceAlert.PushToTalkIdle -> R.string.meeting_voiceAlert_pushToTalk
    MeetingVoiceAlert.GateClosed -> R.string.meeting_voiceAlert_gateClosed
    MeetingVoiceAlert.NotReachingRoom, MeetingVoiceAlert.None ->
        R.string.meeting_voiceAlert_notReachingRoom
}
