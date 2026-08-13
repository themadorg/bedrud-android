package com.bedrud.app.ui.screens.meeting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.bedrud.app.ui.theme.BedrudShapeTokens
import com.bedrud.app.ui.theme.Dimens
import com.bedrud.app.R

/** Placeholder elapsed time until the server exposes recording state. TODO(#107) */
const val RecordingElapsedPlaceholder = "00:10"

/**
 * The "this room is being recorded" notice, shown under the top bar when the recording dot is
 * tapped. Dev-gated at the call site until the server exposes recording state; the elapsed time
 * is a placeholder for the same reason. TODO(#107): drive both from real recording state.
 */
@Composable
fun MeetingRecordingBanner(
    elapsedLabel: String,
    onAcknowledge: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = BedrudShapeTokens.card,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(Dimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.space8),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.meeting_recording_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = elapsedLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = stringResource(R.string.meeting_recording_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onAcknowledge) {
                    Text(stringResource(R.string.meeting_recording_acknowledge))
                }
            }
        }
    }
}
