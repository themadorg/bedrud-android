package com.bedrud.app.ui.screens.meeting

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.bedrud.app.R
import com.bedrud.app.ui.components.BedrudBottomSheet
import com.bedrud.app.ui.components.BedrudSheetActionRow
import com.bedrud.app.ui.components.BedrudSheetTitle
import com.bedrud.app.ui.components.DevHintBadge
import com.bedrud.app.ui.components.DevOnly

/**
 * Long-pressing the stream you're watching opens this sheet. Stream volume needs the share to
 * carry audio, which isn't published yet, so the slider ships as a dev-only "coming soon" row.
 * TODO(#105): wire it once screenshare audio exists. Leaving a stream is reversible, so the
 * action stays neutral — red is reserved for destructive choices.
 */
@Composable
fun MeetingStreamSheet(
    presenterName: String,
    onLeaveStream: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = meetingChromeColors()

    BedrudBottomSheet(onDismiss = onDismiss) {
        BedrudSheetTitle(
            text = stringResource(R.string.meeting_stage_screenSharePresenting, presenterName),
            color = colors.onButton,
        )
        HorizontalDivider(color = colors.divider)

        DevOnly {
            BedrudSheetActionRow(
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                title = stringResource(R.string.meeting_participant_volume),
                contentColor = colors.onButton,
                trailing = { DevHintBadge(stringResource(R.string.common_hint_comingSoon)) },
                onClick = {},
            )
        }

        BedrudSheetActionRow(
            icon = Icons.AutoMirrored.Filled.ExitToApp,
            title = stringResource(R.string.meeting_stream_leave),
            contentColor = colors.onButton,
            onClick = {
                onLeaveStream()
                onDismiss()
            },
        )
    }
}
