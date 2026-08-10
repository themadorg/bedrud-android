package com.bedrud.app.ui.screens.meeting

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.bedrud.app.R
import com.bedrud.app.ui.components.BedrudBottomSheet
import com.bedrud.app.ui.components.BedrudButton
import com.bedrud.app.ui.components.BedrudButtonVariant
import com.bedrud.app.ui.components.BedrudSheetActionRow
import com.bedrud.app.ui.components.BedrudSheetTitle

/**
 * Leave / end-for-everyone chooser, shown to the meeting owner.
 *
 * A sheet rather than an [androidx.compose.material3.AlertDialog]: this is a three-way choice, and a
 * Material dialog carries at most two actions, so the third had to be squeezed into the dialog's
 * text slot where it read as loose text rather than a peer action. Rows also give each choice a line
 * saying what it does to everyone else — the part that actually matters here.
 */
@Composable
fun MeetingLeaveSheet(
    onDismiss: () -> Unit,
    onJustLeave: () -> Unit,
    onEndForEveryone: () -> Unit,
) {
    val colors = meetingChromeColors()

    BedrudBottomSheet(onDismiss = onDismiss) {
        BedrudSheetTitle(
            text = stringResource(R.string.meeting_dialog_leaveTitle),
            color = colors.onButton,
        )

        BedrudSheetActionRow(
            icon = Icons.AutoMirrored.Filled.Logout,
            title = stringResource(R.string.meeting_button_justLeave),
            supportingText = stringResource(R.string.meeting_leave_justLeaveDescription),
            contentColor = colors.onButton,
            supportingColor = colors.onButtonVariant,
            onClick = onJustLeave,
        )

        BedrudSheetActionRow(
            icon = Icons.Default.CallEnd,
            title = stringResource(R.string.meeting_button_endForEveryone),
            supportingText = stringResource(R.string.meeting_leave_endForEveryoneDescription),
            contentColor = colors.endCall,
            supportingColor = colors.onButtonVariant,
            onClick = onEndForEveryone,
        )

        BedrudButton(
            text = stringResource(R.string.common_button_cancel),
            variant = BedrudButtonVariant.GHOST,
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
