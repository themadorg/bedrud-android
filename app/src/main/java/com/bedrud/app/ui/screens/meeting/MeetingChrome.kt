package com.bedrud.app.ui.screens.meeting

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.bedrud.app.ui.components.BedrudBottomSheet

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
 * The in-meeting sheets, on the app's shared [BedrudBottomSheet].
 *
 * All this adds is the meeting chrome's own palette — the call UI sits on a dark overlay, so its
 * sheets are darker than the app default. Everything else (shape, drag handle, insets, gutter)
 * comes from the shared component so these sheets match the rest of the app.
 */
@Composable
fun MeetingBottomSheet(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = meetingChromeColors()
    BedrudBottomSheet(
        onDismiss = onDismiss,
        containerColor = colors.sheet,
        dragHandleColor = colors.dragHandle,
        content = content,
    )
}