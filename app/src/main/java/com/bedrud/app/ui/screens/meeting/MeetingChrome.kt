package com.bedrud.app.ui.screens.meeting

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class MeetingChromeColors(
    val bar: Color,
    val button: Color,
    val buttonActive: Color,
    val buttonMediaOff: Color,
    val onButtonMediaOff: Color,
    val onButton: Color,
    val onButtonVariant: Color,
    val divider: Color,
    val dragHandle: Color,
    val sheet: Color,
    val selected: Color,
    val accent: Color,
    val warning: Color,
    val onWarning: Color,
    val endCall: Color,
    val onEndCall: Color,
)

@Composable
fun meetingChromeColors(): MeetingChromeColors {
    val scheme = MaterialTheme.colorScheme
    return MeetingChromeColors(
        bar = scheme.surfaceContainerHigh,
        button = scheme.surfaceVariant,
        buttonActive = scheme.secondary,
        buttonMediaOff = scheme.surfaceContainerHighest,
        onButtonMediaOff = scheme.onSurfaceVariant,
        onButton = scheme.onSurface,
        onButtonVariant = scheme.onSurfaceVariant,
        divider = scheme.outline.copy(alpha = 0.45f),
        dragHandle = scheme.onSurfaceVariant.copy(alpha = 0.55f),
        sheet = scheme.surface,
        selected = scheme.secondary,
        accent = scheme.primary,
        warning = scheme.error,
        onWarning = scheme.onError,
        endCall = scheme.error,
        onEndCall = scheme.onError,
    )
}

/**
 * Shared modal bottom-sheet scaffold for the in-meeting sheets: the meeting drag handle, sheet
 * surface color, and a navigation-bar-padded content column. Callers supply only their content.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetingBottomSheet(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
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
            content = content,
        )
    }
}