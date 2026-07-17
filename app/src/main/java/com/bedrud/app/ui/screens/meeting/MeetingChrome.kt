package com.bedrud.app.ui.screens.meeting

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

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